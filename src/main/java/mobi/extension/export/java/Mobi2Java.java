package mobi.extension.export.java;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mobi.core.Mobi;
import mobi.core.cardinality.Cardinality;
import mobi.core.common.Relation;
import mobi.core.concept.Attribute;
import mobi.core.concept.Class;
import mobi.core.concept.Instance;
import mobi.core.relation.CompositionRelation;
import mobi.core.relation.InheritanceRelation;
import mobi.core.relation.InstanceRelation;
import mobi.core.relation.SymmetricRelation;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCatchBlock;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JSwitch;
import com.sun.codemodel.JTryBlock;
import com.sun.codemodel.JVar;

/**
 * 
 * @author Andr� Schmid - andrehschmid at gmail dot com
 * 
 */
public class Mobi2Java {

	private final JCodeModel codeModel;
	private final String pathToGenerate;
	private final Mobi mobi;

	private final Map<String, String> classes;

	public Mobi2Java(final Mobi mobi, final String pathToGenerate) {
		this.mobi = mobi;
		this.pathToGenerate = pathToGenerate;
		this.codeModel = new JCodeModel();
		this.classes = new HashMap<String, String>();
		this.initialize();
	}

	/**
	 * Generates the physical files to the disk from the this.codeModel
	 * 
	 * @throws IOException
	 */
	public void generateJavaFiles() throws IOException {
		File file = new File(this.pathToGenerate);
		file.mkdirs();
		this.codeModel.build(file);
	}

	private void generateClassAttributes(final JDefinedClass definedClass)
			throws Exception {
		System.out.println("generateClassAttributes for " + definedClass.name()
				+ " - " + definedClass.fullName());

		Class mobiClass = this.mobi.getClass(definedClass.name());
		System.out.println("teste " + mobiClass.toString());

		for (Attribute attribute : this.mobi.getAllClassAttributes(mobiClass)) {
			// TODO implementar tipo do Atributo

			System.out.println("field " + attribute.getUri() + " for "
					+ definedClass.name() + " - " + definedClass.fullName());
			JFieldVar newAttribute;

			switch (attribute.getType()) {
			case STRING:
				newAttribute = definedClass.field(JMod.PRIVATE, String.class,
						attribute.getUri());
				break;
			case INTEGER:
				newAttribute = definedClass.field(JMod.PRIVATE, Integer.class,
						attribute.getUri());
				break;
			default:
				throw new Exception("Attribute type not supported");
			}
			this.generateGetMethod(definedClass, newAttribute);
			this.generateSetMethod(definedClass, newAttribute);
		}
	}

	/**
	 * Generates all basic classes for the Java code structure: mobi.Concept,
	 * mobi.Relation, mobi.ClassMobi and factory.Factory
	 */
	public void initialize() {
		try {
			JDefinedClass conceitoClass = this.codeModel._class("mobi.Concept");
			JFieldVar uriAttribute = conceitoClass.field(JMod.PRIVATE,
					String.class, "uri");
			this.generateGetMethod(conceitoClass, uriAttribute);
			this.generateSetMethod(conceitoClass, uriAttribute);

			JDefinedClass relationClass = this.codeModel
					._class("mobi.Relation");
			relationClass._extends(conceitoClass);

			JDefinedClass classMobiClass = this.codeModel
					._class("mobi.ClassMobi");
			classMobiClass._extends(conceitoClass);

			JFieldVar typeAttribute = relationClass.field(JMod.PRIVATE,
					this.codeModel.INT, "type");
			this.generateGetMethod(relationClass, typeAttribute);
			this.generateSetMethod(relationClass, typeAttribute);

			relationClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL,
					this.codeModel.INT, "ZERO_ONE", JExpr.lit(1));
			relationClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL,
					this.codeModel.INT, "ONE_ONE", JExpr.lit(2));
			relationClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL,
					this.codeModel.INT, "ZERO_N", JExpr.lit(3));
			relationClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL,
					this.codeModel.INT, "ONE_N", JExpr.lit(4));

			JFieldVar instanciaMapAttribute = relationClass.field(
					JMod.PRIVATE,
					this.codeModel.ref(Map.class).narrow(String.class)
							.narrow(classMobiClass),
					"instanciaMap",
					JExpr._new(this.codeModel.ref(HashMap.class)
							.narrow(String.class).narrow(classMobiClass)));
			this.generateGetMethod(relationClass, instanciaMapAttribute);
			this.generateSetMethod(relationClass, instanciaMapAttribute);
			JMethod addToInstanciaMapMethod = this.generateAddToMapMethod(
					relationClass, instanciaMapAttribute, classMobiClass);
			// JVar classMobiParam = addToInstanciaMapMethod.params().get(0);
			addToInstanciaMapMethod.body().pos(0);
			JSwitch addToInstanciaMapMethodSwitch = addToInstanciaMapMethod
					.body()._switch(relationClass.fields().get("type"));
			addToInstanciaMapMethodSwitch._case(relationClass
					.staticRef("ZERO_ONE"));
			JBlock relationOneOneSwitchBlock = addToInstanciaMapMethodSwitch
					._case(relationClass.staticRef("ONE_ONE")).body();
			relationOneOneSwitchBlock
					._if(relationClass.fields().get("instanciaMap")
							.invoke("keySet").invoke("size").gt(JExpr.lit(0)))
					._then()
					._throw(JExpr._new(this.codeModel._ref(Exception.class))
							.arg(JExpr.lit("Relation out of bound")));
			relationOneOneSwitchBlock._break();

			// case Relation.ONE_N:
			// case Relation.ONE_ONE: {
			// if (instancia.keySet().size() == 1) {
			// throw new Exception();
			// }
			// break;
			// }
			// }

			JMethod removeFromInstanciaMapMethod = this
					.generateRemoveFromMapMethod(relationClass,
							instanciaMapAttribute, classMobiClass);
			// JVar classMobiRemoveFromMethodParam =
			// removeFromInstanciaMapMethod.params().get(0);
			removeFromInstanciaMapMethod.body().pos(0);
			JSwitch removeFromInstanciaMapMethodSwitch = removeFromInstanciaMapMethod
					.body()._switch(relationClass.fields().get("type"));
			removeFromInstanciaMapMethodSwitch._case(relationClass
					.staticRef("ONE_N"));
			JBlock removeFromOneOneSwitchBlock = removeFromInstanciaMapMethodSwitch
					._case(relationClass.staticRef("ONE_ONE")).body();
			removeFromOneOneSwitchBlock
					._if(relationClass.fields().get("instanciaMap")
							.invoke("keySet").invoke("size").eq(JExpr.lit(1)))
					._then()
					._throw(JExpr._new(this.codeModel._ref(Exception.class))
							.arg(JExpr.lit("Relation out of bound")));
			removeFromOneOneSwitchBlock._break();

			JFieldVar relationMapAttribute = classMobiClass.field(
					JMod.PRIVATE,
					this.codeModel.ref(Map.class).narrow(String.class)
							.narrow(relationClass),
					"relationMap",
					JExpr._new(this.codeModel.ref(HashMap.class)
							.narrow(String.class).narrow(relationClass)));
			this.generateGetMethod(classMobiClass, relationMapAttribute);
			this.generateSetMethod(classMobiClass, relationMapAttribute);
			this.generateAddToMapMethod(classMobiClass, relationMapAttribute,
					relationClass);

			/**
			 * public Relation getRelation(String uri) { return ((Relation)
			 * this.relationMap.get(uri)); }
			 */

			JMethod classMobiGetRelationMethod = classMobiClass.method(
					JMod.PUBLIC, relationClass, "get" + relationClass.name());
			JVar classMobiGetRelationMethodParam = classMobiGetRelationMethod
					.param(String.class, "uri");
			classMobiGetRelationMethod.body()._return(
					JExpr.cast(
							relationClass,
							JExpr._this().ref(relationMapAttribute)
									.invoke("get")
									.arg(classMobiGetRelationMethodParam)));

			JDefinedClass factoryClass = this.codeModel._class(JMod.PUBLIC
					| JMod.ABSTRACT, "factory.Factory", ClassType.CLASS);
			JMethod createMethod = factoryClass.method(JMod.PUBLIC,
					classMobiClass, "create");
			createMethod._throws(Exception.class);
			JMethod createClassMethod = factoryClass.method(JMod.PROTECTED
					| JMod.ABSTRACT, classMobiClass, "createClass");
			createClassMethod.param(String.class, "uri");
			JMethod createRelation = factoryClass.method(JMod.PROTECTED
					| JMod.ABSTRACT, this.codeModel.VOID, "createRelation");
			createRelation._throws(Exception.class);
			createRelation.param(classMobiClass, "classMobi");
			JVar factoryMethodParam = createMethod.param(String.class, "uri");
			JVar classMobiVar = createMethod.body().decl(classMobiClass,
					"classMobi",
					JExpr.invoke(createClassMethod).arg(factoryMethodParam));
			createMethod.body().add(
					JExpr.invoke(createRelation).arg(classMobiVar));
			createMethod.body()._return(classMobiVar);

		} catch (JClassAlreadyExistsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// TODO documentar generateMainClass()
	/**
	 * 
	 */
	public void generateMainClass() {
		try {
			JDefinedClass classMobiClass = this.codeModel._class("main.Main");
			JMethod mainMethod = classMobiClass.method(JMod.PUBLIC
					| JMod.STATIC, this.codeModel.VOID, "main");
			mainMethod.param(this.codeModel.ref(String[].class), "args");
			JTryBlock tryBlock = mainMethod.body()._try();
			JCatchBlock catchBlock = tryBlock._catch(this.codeModel
					.ref(Exception.class));
			catchBlock.body().add(
					catchBlock.param("e").invoke("printStackTrace"));

			HashMap<String, JVar> instanceDeclarationMap = new HashMap<String, JVar>();

			HashMap<String, InheritanceRelation> inheritanceRelationMap = this.mobi
					.getAllInheritanceRelations();
			List<String> generatedInheritanceUri = new ArrayList<String>();
			for (String relationUri : inheritanceRelationMap.keySet()) {
				if (!generatedInheritanceUri.contains(relationUri)) {
					generatedInheritanceUri.add(relationUri);
					InheritanceRelation inheritanceRelation = inheritanceRelationMap
							.get(relationUri);

					for (InstanceRelation instanceRelation : inheritanceRelation
							.getInstanceRelationMapB().values()) {
						String instanceName = instanceRelation.getInstance()
								.getUri();
						String className = inheritanceRelation.getClassB()
								.getUri();
						if (!instanceDeclarationMap.containsKey(instanceName)) {
							JVar var = tryBlock
									.body()
									.decl(this.codeModel._getClass("domain."
											+ className),
											instanceName,
											JExpr.cast(
													this.codeModel
															._getClass("domain."
																	+ className),
													this.codeModel
															._getClass(
																	"factory."
																			+ className
																			+ "Factory")
															.staticInvoke(
																	"getInstance")
															.invoke("create")
															.arg(instanceName)));

							instanceDeclarationMap.put(instanceName, var);
						}
					}

					JDefinedClass definedClassA = this.codeModel
							._getClass(this.classes.get(inheritanceRelation
									.getClassA().getUri()));
					JDefinedClass definedClassB = this.codeModel
							._getClass(this.classes.get(inheritanceRelation
									.getClassB().getUri()));

					definedClassB._extends(definedClassA);
					/*
					 * generateCompositionRelation(symmetricRelation.getName(),
					 * definedClassA, definedClassB, symmetricRelation
					 * .getCardinalityA().getType());
					 */

				}
			}

			HashMap<String, Set<Class>> instanceClassRelationMap = this.mobi
					.getAllInstanceClassRelation();
			for (String instanceName : instanceClassRelationMap.keySet()) {
				// Cliente iCliente1 = (Cliente)
				// ClienteFactory.getInstance().create(
				// "Cliente1");

				if (!instanceDeclarationMap.containsKey(instanceName)) {
					Iterator<Class> classIterator = instanceClassRelationMap
							.get(instanceName).iterator();
					while (classIterator.hasNext()
							&& !instanceDeclarationMap
									.containsKey(instanceName)) {
						String className = classIterator.next().getUri();

						JVar var = tryBlock
								.body()
								.decl(this.codeModel._getClass("domain."
										+ className),
										instanceName,
										JExpr.cast(
												this.codeModel
														._getClass("domain."
																+ className),
												this.codeModel
														._getClass(
																"factory."
																		+ className
																		+ "Factory")
														.staticInvoke(
																"getInstance")
														.invoke("create")
														.arg(instanceName)));

						instanceDeclarationMap.put(instanceName, var);
					}
				}
			}
			HashMap<String, CompositionRelation> allCompositions = this.mobi
					.getAllCompositionRelations();
			List<String> generatedCompositionsUri = new ArrayList<String>();
			for (String compositionUri : allCompositions.keySet()) {
				if (!generatedCompositionsUri.contains(compositionUri)) {
					generatedCompositionsUri.add(compositionUri);
					CompositionRelation compositionRelation = allCompositions
							.get(compositionUri);
					// Relation.UNIDIRECIONAL_COMPOSITION

					for (InstanceRelation instanceRelation : compositionRelation
							.getInstanceRelationMapA().values()) {
						// Relation rCliente_Tem_Pedido = iCliente1
						// .getRelation(ClienteFactory.CLIENTE_TEM_PEDIDO);
						// rCliente_Tem_Pedido.addToInstanciaMap(iPedido1);
						// JVar relation =
						// relationVarList.get(compositionRelation.getNameA().toUpperCase());
						// if(relation == null){
						// relation = tryBlock.body().decl(
						// codeModel._getClass("mobi.Relation"),
						// "r" + instanceRelation.getInstance()
						// .getUri()
						// + compositionRelation.getNameA()
						// .toUpperCase(),
						// instanceDeclarationMap
						// .get(instanceRelation.getInstance()
						// .getUri())
						// .invoke("getRelation")
						// .arg(codeModel._getClass(
						// "factory."
						// + compositionRelation
						// .getClassA()
						// .getUri()
						// + "Factory").staticRef(
						// compositionRelation.getNameA()
						// .toUpperCase())));
						// }
						if (instanceRelation.getAllInstances().values().size() > 0) {
							JVar relation = tryBlock
									.body()
									.decl(this.codeModel
											._getClass("mobi.Relation"),
											"r"
													+ instanceRelation
															.getInstance()
															.getUri()
													+ "_"
													+ compositionRelation
															.getNameA()
															.toUpperCase(),
											instanceDeclarationMap
													.get(instanceRelation
															.getInstance()
															.getUri())
													.invoke("getRelation")
													.arg(this.codeModel
															._getClass(
																	"factory."
																			+ compositionRelation
																					.getClassA()
																					.getUri()
																			+ "Factory")
															.staticRef(
																	compositionRelation
																			.getNameA()
																			.toUpperCase())));

							for (Instance instance : instanceRelation
									.getAllInstances().values()) {
								tryBlock.body()
										.add(relation
												.invoke("addToInstanciaMap")
												.arg(instanceDeclarationMap
														.get(instance.getUri())));
							}
						}

					}

					if ((compositionRelation.getType() == Relation.BIDIRECIONAL_COMPOSITION)
							|| (compositionRelation.getType() == Relation.BIDIRECIONAL_COMPOSITION_HAS_BELONGS_TO)) {
						for (InstanceRelation instanceRelation : compositionRelation
								.getInstanceRelationMapB().values()) {
							if (instanceRelation.getAllInstances().values()
									.size() > 0) {
								JVar relation = tryBlock
										.body()
										.decl(this.codeModel
												._getClass("mobi.Relation"),
												"r"
														+ instanceRelation
																.getInstance()
																.getUri()
														+ "_"
														+ compositionRelation
																.getNameB()
																.toUpperCase(),
												instanceDeclarationMap
														.get(instanceRelation
																.getInstance()
																.getUri())
														.invoke("getRelation")
														.arg(this.codeModel
																._getClass(
																		"factory."
																				+ compositionRelation
																						.getClassB()
																						.getUri()
																				+ "Factory")
																.staticRef(
																		compositionRelation
																				.getNameB()
																				.toUpperCase())));

								for (Instance instance : instanceRelation
										.getAllInstances().values()) {
									tryBlock.body()
											.add(relation.invoke(
													"addToInstanciaMap").arg(
													instanceDeclarationMap
															.get(instance
																	.getUri())));
								}
							}

						}

					}
				}
			}

		} catch (JClassAlreadyExistsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Creates the domain.[uri] class that will contains all the Mobi2Java
	 * generated code
	 * 
	 * @param uri
	 *            The class name from MOBI
	 * @return The generated class on JCodeModel
	 * @throws Exception
	 */
	private JDefinedClass generateDomainClass(final String uri)
			throws Exception {
		System.out.println("domain." + uri);
		JDefinedClass definedClass = this.codeModel._class("domain." + uri);
		definedClass._extends(this.codeModel._getClass("mobi.ClassMobi"));
		try {
			this.generateClassAttributes(definedClass);
		} catch (Exception e) {
			throw e;
		}
		return definedClass;
	}

	/**
	 * Creates the domain.[motherClassName]User class that will be used for
	 * domain edition , without the need to change a MOBI generated domain
	 * class.
	 * 
	 * @param motherClassName
	 *            The class name that will be extented
	 * @return The generated class on JCodeModel
	 * @throws Exception
	 */
	public JDefinedClass generateDomainUserClass(final String motherClassName)
			throws Exception {
		System.out.println("domain." + motherClassName);
		JDefinedClass definedClass = this.codeModel._class("domain."
				+ motherClassName + "User");
		definedClass._extends(this.codeModel._getClass("domain."
				+ motherClassName));
		// TODO constructor super
		return definedClass;
	}

	/**
	 * Finds all Domain Classes from MOBI through the mobi.getAllClasses()
	 * method and generates the necessary Java code to represent them. Stores
	 * generated class in the HashMap classes for future references.
	 */
	public void generateAllDomainClasses() {
		HashMap<String, Class> mobiClasses = this.mobi.getAllClasses();
		for (String key : mobiClasses.keySet()) {
			JDefinedClass newDomainClass;
			try {
				newDomainClass = this.generateDomainClass(key);
				this.generateDomainUserClass(key);
				this.classes.put(newDomainClass.name(),
						newDomainClass.fullName());
			} catch (JClassAlreadyExistsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	// TODO finalizar a documenta��o
	/**
	 * 
	 * @param uri
	 * @return
	 * @throws JClassAlreadyExistsException
	 */

	public JDefinedClass generateFactoryClass(final String uri)
			throws JClassAlreadyExistsException {
		/**
		 * Declaring *Factory class public class [uri]Factory extends Factory
		 */
		JDefinedClass definedClass = this.codeModel._class("factory." + uri
				+ "Factory");
		definedClass._extends(this.codeModel._getClass("factory.Factory"));

		/**
		 * Declaring [uri]Factory static attribute private static [uri]Factory
		 * [uri]Factory = new [uri]Factory();
		 */
		JFieldVar factoryField = definedClass.field(
				JMod.PRIVATE | JMod.STATIC,
				definedClass,
				definedClass.name().substring(0, 1).toLowerCase()
						+ definedClass.name().substring(1,
								definedClass.name().length()),
				JExpr._new(definedClass));

		/**
		 * Creating getInstance method
		 */
		definedClass
				.method(JMod.PUBLIC | JMod.STATIC, definedClass, "getInstance")
				.body()._return(definedClass.staticRef(factoryField));

		/**
		 * Overriding createClass method
		 */
		JMethod createClassMethod = definedClass.method(JMod.PUBLIC,
				this.codeModel._getClass("mobi.ClassMobi"), "createClass");
		createClassMethod.annotate(Override.class);
		JVar createClassMethodParam = createClassMethod.param(String.class,
				"uri");
		JVar createClassMethodVar = createClassMethod.body().decl(
				this.codeModel._getClass("mobi.ClassMobi"), "classMobi",
				JExpr._new(this.codeModel._getClass("domain." + uri)));

		createClassMethod.body().add(
				createClassMethodVar.invoke("setUri").arg(
						createClassMethodParam));
		createClassMethod.body()._return(createClassMethodVar);

		/**
		 * Overriding createRelation method
		 */
		JMethod createRelationMethod = definedClass.method(JMod.PROTECTED,
				this.codeModel.VOID, "createRelation");
		createRelationMethod._throws(Exception.class);
		createRelationMethod.annotate(Override.class);
		createRelationMethod.param(this.codeModel._getClass("mobi.ClassMobi"),
				"classMobi");

		// createClassMethod.body().add(JExpr.assign(lhs, rhs));

		//
		// @Override
		// public ClassMobi criaClass(String uri) {
		// ClassMobi p= new Cliente();
		//
		// p.setUri(uri);
		// return p;
		// }
		//
		// @Override
		// protected void criaRelation(ClassMobi c) {
		//
		// Relation r = new Relation();
		// r.setUri(FactoryCliente.CLIENTE_TEM_PEDIDO);
		// c.addRelation(r);
		//
		// Relation r1 = new Relation();
		// r1.setUri(FactoryCliente.CLIENTE_TEM_ENDERECO);
		// c.addRelation(r1);
		//
		// }

		return definedClass;

	}

	/**
	 * Finds all domain generated classes Mobi2Java codeModel and generates the
	 * related Factory ([className]Factory)
	 */
	public void generateAllFactoryClasses() {
		ArrayList<String> classes = new ArrayList<String>(this.classes.keySet());

		for (String key : classes) {
			JDefinedClass newFactoryClass;
			try {
				newFactoryClass = this.generateFactoryClass(key);
				this.classes.put(newFactoryClass.name(),
						newFactoryClass.fullName());
			} catch (JClassAlreadyExistsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * Generates a composition relation given the relation name, participants
	 * classes and the cardinality type
	 * 
	 * @param name
	 *            The name of the relation
	 * @param definedClassA
	 *            The class to receive the relation
	 * @param definedClassB
	 *            The related class
	 * @param cardinalityType
	 *            The type of the cardinality: zero to one, one to one, one to
	 *            many, zero to many
	 */
	public void generateCompositionRelation(final String name,
			final JDefinedClass definedClassA,
			final JDefinedClass definedClassB, final int cardinalityType) {
		// JDefinedClass definedClassA = codeModel._getClass(classes
		// .get(compositionRelation.getClassA().getUri()));
		JDefinedClass definedClassFactory = this.codeModel
				._getClass(this.classes.get(definedClassA.name() + "Factory"));

		JFieldVar factoryAField = definedClassFactory.field(JMod.PUBLIC
				| JMod.STATIC | JMod.FINAL, String.class, name.toUpperCase(),
				JExpr.lit(name.toUpperCase()));

		// TODO tentar utilizar o JDefinedClass.getMethod para pegar o
		// m�todo createRelation
		JMethod createRelationMethod = null;
		for (JMethod method : definedClassFactory.methods()) {
			if (method.name().equals("createRelation")) {
				createRelationMethod = method;
				break;
			}
		}

		JDefinedClass relationClass = this.codeModel._getClass("mobi.Relation");
		JFieldVar relationCardinalityType = null;
		switch (cardinalityType) {
		case Cardinality.ONE_N:
			relationCardinalityType = relationClass.fields().get("ONE_N");
			break;
		case Cardinality.ONE_ONE:
			relationCardinalityType = relationClass.fields().get("ONE_ONE");
			break;
		case Cardinality.ZERO_N:
			relationCardinalityType = relationClass.fields().get("ZERO_N");
			break;
		case Cardinality.ZERO_ONE:
			relationCardinalityType = relationClass.fields().get("ZERO_ONE");
			break;
		}

		// public ArrayList<Pedido> getAllPedido() {
		// Relation relation = getRelation(ClienteFactory.CLIENTE_TEM_PEDIDO);
		// ArrayList<Pedido> pedidoList = new ArrayList<Pedido>();
		// Iterator<ClassMobi> it =
		// relation.getInstanciaMap().values().iterator();
		// while(it.hasNext()){
		// pedidoList.add((Pedido) it.next());
		// }
		//
		// return pedidoList;
		// }

		switch (cardinalityType) {
		case Cardinality.ONE_N:
		case Cardinality.ZERO_N: {
			JBlock getMethodBlock = definedClassA.method(JMod.PUBLIC,
					this.codeModel.ref(ArrayList.class).narrow(definedClassB),
					"getAll" + definedClassB.name()).body();

			JVar getMethodRelation = getMethodBlock.decl(
					this.codeModel._getClass("mobi.Relation"),
					"relation",
					JExpr.invoke("getRelation").arg(
							definedClassFactory.staticRef(definedClassFactory
									.fields().get(name.toUpperCase()))));

			JVar getMethodArrayList = getMethodBlock.decl(
					this.codeModel.ref(ArrayList.class).narrow(definedClassB),
					definedClassB.name().substring(0, 1).toLowerCase()
							+ definedClassB.name().substring(1,
									definedClassB.name().length()) + "List",
					JExpr._new(this.codeModel.ref(ArrayList.class).narrow(
							definedClassB)));

			JVar getMethodIterator = getMethodBlock.decl(
					this.codeModel.ref(Iterator.class).narrow(
							this.codeModel._getClass("mobi.ClassMobi")), "it",
					getMethodRelation.invoke("getInstanciaMap")
							.invoke("values").invoke("iterator"));

			getMethodBlock
					._while(getMethodIterator.invoke("hasNext"))
					.body()
					.add(getMethodArrayList.invoke("add").arg(
							JExpr.cast(definedClassB,
									getMethodIterator.invoke("next"))));

			getMethodBlock._return(getMethodArrayList);
			break;
		}
		case Cardinality.ONE_ONE:
		case Cardinality.ZERO_ONE: {
			JBlock getMethodBlock = definedClassA.method(JMod.PUBLIC,
					definedClassB, "get" + definedClassB.name()).body();
			JVar getMethodRelation = getMethodBlock.decl(
					this.codeModel._getClass("mobi.Relation"),
					"relation",
					JExpr.invoke("getRelation").arg(
							definedClassFactory.staticRef(definedClassFactory
									.fields().get(name.toUpperCase()))));
			JVar getMethodInstanceList = getMethodBlock
					.decl(this.codeModel.ref(ArrayList.class).narrow(
							this.codeModel._getClass("mobi.ClassMobi")),
							"instanceList",
							JExpr._new(
									this.codeModel
											.ref(ArrayList.class)
											.narrow(this.codeModel
													._getClass("mobi.ClassMobi")))
									.arg(getMethodRelation.invoke(
											"getInstanciaMap").invoke("values")));
			getMethodBlock._return(JExpr.cast(definedClassB,
					getMethodInstanceList.invoke("get").arg(JExpr.lit(0))));
			break;
		}
		}

		// TODO verificar uma forma de criar uma variavel relation �nica
		// (melhorar)
		JVar createRelationMethodVar = createRelationMethod.body().decl(
				this.codeModel._getClass("mobi.Relation"),
				"r" + name.toUpperCase(),
				JExpr._new(this.codeModel._getClass("mobi.Relation")));
		createRelationMethod
				.body()
				.add(createRelationMethodVar.invoke("setUri").arg(
						definedClassFactory.staticRef(factoryAField)))
				.add(createRelationMethodVar.invoke("setType").arg(
						relationClass.staticRef(relationCardinalityType)))
				.add(createRelationMethod.params().get(0)
						.invoke("addToRelationMap")
						.arg(createRelationMethodVar));
	}

	/**
	 * @author Andr� Schmid Finds all Composition Relations from MOBI through
	 *         the mobi.getAllCompositionRelations() method and generates the
	 *         necessary Java code to represent them. If a Relation is
	 *         bidirectional (Relation.BIDIRECIONAL_COMPOSITION) it generates
	 *         the inverse attribute.
	 */
	public void generateAllCompositionRelations() {
		HashMap<String, CompositionRelation> allCompositions = this.mobi
				.getAllCompositionRelations();
		List<String> generatedCompositionsUri = new ArrayList<String>();
		System.out.println("key set " + allCompositions.keySet());

		for (String compositionUri : allCompositions.keySet()) {
			System.out.println(compositionUri);
			System.out.println(generatedCompositionsUri);
			System.out.println(generatedCompositionsUri
					.contains(compositionUri));

			if (!generatedCompositionsUri.contains(compositionUri)) {
				generatedCompositionsUri.add(compositionUri);
				CompositionRelation compositionRelation = allCompositions
						.get(compositionUri);
				System.out.println("Composition type: "
						+ compositionRelation.getType());

				System.out.println(compositionRelation.getNameA());
				System.out.println(compositionRelation.getClassA());
				System.out.println(compositionRelation.getCardinalityA());

				System.out.println(compositionRelation.getNameB());
				System.out.println(compositionRelation.getClassB());
				System.out.println(compositionRelation.getCardinalityB());

				// Relation.UNIDIRECIONAL_COMPOSITION
				JDefinedClass definedClassA = this.codeModel
						._getClass(this.classes.get(compositionRelation
								.getClassA().getUri()));
				JDefinedClass definedClassB = this.codeModel
						._getClass(this.classes.get(compositionRelation
								.getClassB().getUri()));
				this.generateCompositionRelation(
						compositionRelation.getNameA(), definedClassA,
						definedClassB, compositionRelation.getCardinalityA()
								.getType());

				this.generationMethodsCRUD(compositionRelation.getNameA(),
						definedClassA, definedClassB);

				switch (compositionRelation.getType()) {
				case Relation.BIDIRECIONAL_COMPOSITION_HAS_BELONGS_TO:
				case Relation.BIDIRECIONAL_COMPOSITION: {
					this.generateCompositionRelation(compositionRelation
							.getNameB(), definedClassB, definedClassA,
							compositionRelation.getCardinalityB().getType());
					break;
				}
				}
			}
		}
	}

	/**
	 * @author Andr� Schmid Finds all Inheritance Relations from MOBI through
	 *         the mobi.getAllInheritanceRelations() method and generates the
	 *         necessary Java code to represent them
	 * 
	 */
	public void generateAllInheritanceRelations() {
		HashMap<String, InheritanceRelation> inheritanceRelationMap = this.mobi
				.getAllInheritanceRelations();
		List<String> generatedInheritanceUri = new ArrayList<String>();
		for (String relationUri : inheritanceRelationMap.keySet()) {
			if (!generatedInheritanceUri.contains(relationUri)) {
				generatedInheritanceUri.add(relationUri);
				InheritanceRelation inheritanceRelation = inheritanceRelationMap
						.get(relationUri);

				JDefinedClass definedClassA = this.codeModel
						._getClass(this.classes.get(inheritanceRelation
								.getClassA().getUri()));
				JDefinedClass definedClassB = this.codeModel
						._getClass(this.classes.get(inheritanceRelation
								.getClassB().getUri()));
				JDefinedClass definedClassAFactory = this.codeModel
						._getClass(this.classes.get(inheritanceRelation
								.getClassA().getUri() + "Factory"));
				JDefinedClass definedClassBFactory = this.codeModel
						._getClass(this.classes.get(inheritanceRelation
								.getClassB().getUri() + "Factory"));
				definedClassB._extends(definedClassA);
				definedClassBFactory._extends(definedClassAFactory);

				Iterator<JMethod> definedClassBFactoryMethodIt = definedClassBFactory
						.methods().iterator();
				JMethod createRelation = null;
				while (definedClassBFactoryMethodIt.hasNext()
						&& (createRelation == null)) {
					JMethod thisMethod = definedClassBFactoryMethodIt.next();
					if (thisMethod.name() == "createRelation") {
						createRelation = thisMethod;
					}
				}
				definedClassBFactory.methods().remove(createRelation);
			}
		}
	}

	// TODO generateAllSymmetricRelation
	public void generateAllSymmetricRelations() {
		HashMap<String, SymmetricRelation> symmetricRelationMap = this.mobi
				.getAllSymmetricRelations();
		List<String> generatedSymmetricUri = new ArrayList<String>();
		for (String relationUri : symmetricRelationMap.keySet()) {
			System.out.println(relationUri);
			System.out.println(generatedSymmetricUri);
			System.out.println(generatedSymmetricUri.contains(relationUri));
			if (!generatedSymmetricUri.contains(relationUri)) {
				generatedSymmetricUri.add(relationUri);
				SymmetricRelation symmetricRelation = symmetricRelationMap
						.get(relationUri);
				System.out.println(symmetricRelation.getName());
				System.out.println(symmetricRelation.getClassA());
				System.out.println(symmetricRelation.getCardinalityA());

				System.out.println(symmetricRelation.getName());
				System.out.println(symmetricRelation.getClassB());
				System.out.println(symmetricRelation.getCardinalityB());
				System.out.println(symmetricRelation.getType());

				JDefinedClass definedClassA = this.codeModel
						._getClass(this.classes.get(symmetricRelation
								.getClassA().getUri()));
				JDefinedClass definedClassB = this.codeModel
						._getClass(this.classes.get(symmetricRelation
								.getClassB().getUri()));
				/*
				 * generateCompositionRelation(symmetricRelation.getName(),
				 * definedClassA, definedClassB, symmetricRelation
				 * .getCardinalityA().getType());
				 */

			}
		}
	}

	/**
	 * Calls all necessary functions to generate Java code
	 * 
	 * @throws IOException
	 */
	public void generateAll() throws IOException {
		this.generateAllDomainClasses();
		this.generateAllFactoryClasses();
		this.generateAllCompositionRelations();
		this.generateAllSymmetricRelations();
		this.generateAllInheritanceRelations();
		this.generateMainClass();
		this.generateJavaFiles();
	}

	/**
	 * Generates a public [classType] get() method to the given class
	 * 
	 * @param definedClass
	 *            The class to generate the method
	 * @param attribute
	 *            The class attribute to be returned
	 */
	private void generateGetMethod(final JDefinedClass definedClass,
			final JFieldVar attribute) {
		JMethod getMethod = definedClass.method(
				JMod.PUBLIC,
				attribute.type(),
				"get"
						+ attribute.name().substring(0, 1).toUpperCase()
						+ attribute.name().substring(1,
								attribute.name().length()));

		getMethod.body()._return(attribute);
	}

	/**
	 * Generates a public void set([classType]) method to the given class
	 * 
	 * @param definedClass
	 *            The class to generate the method
	 * @param attribute
	 *            The class attribute to be modified
	 */
	private void generateSetMethod(final JDefinedClass definedClass,
			final JFieldVar attribute) {
		JMethod setMethod = definedClass.method(
				JMod.PUBLIC,
				this.codeModel.VOID,
				"set"
						+ attribute.name().substring(0, 1).toUpperCase()
						+ attribute.name().substring(1,
								attribute.name().length()));
		setMethod.param(attribute.type(), attribute.name());

		JBlock methodBody = setMethod.body();
		methodBody.assign(
				JExpr._this().ref(definedClass.fields().get(attribute.name())),
				attribute);
	}

	private void generateEqualsMethod(final JDefinedClass definedClass) {
		JMethod equalsMethod = definedClass.method(JMod.PUBLIC,
				this.codeModel.BOOLEAN, "equals");
		equalsMethod.annotate(java.lang.Override.class);
		JVar objParam = equalsMethod.param(Object.class, "obj");
		JBlock methodBody = equalsMethod.body();

		/**
		 * obj == null
		 */
		methodBody._if(objParam.eq(JExpr._null()))._then()._return(JExpr.FALSE);
		/**
		 * this == obj
		 */
		methodBody._if(JExpr._this().eq(objParam))._then()._return(JExpr.TRUE);

		/**
		 * !(obj instanceof TheClass)
		 */
		methodBody._if(objParam._instanceof(definedClass).not())._then()
				._return(JExpr.FALSE);
		/**
		 * TheClass other = (TheClass) obj;
		 */
		JVar other = methodBody.decl(definedClass, "other",
				JExpr.cast(definedClass, objParam));
		methodBody
				._if(other.ref("id").invoke("equals").arg(objParam.ref("id"))
						.not())._then()._return(JExpr.FALSE);

		methodBody.directStatement("//TODO");
		methodBody._return(JExpr.TRUE);

	}

	public JMethod generateAddToMapMethod(final JDefinedClass definedClass,
			final JFieldVar mapAttribute, final JDefinedClass paramType) {
		JMethod addToMapMethod = definedClass.method(
				JMod.PUBLIC,
				this.codeModel.VOID,
				"addTo"
						+ mapAttribute.name().substring(0, 1).toUpperCase()
						+ mapAttribute.name().substring(1,
								mapAttribute.name().length()));
		addToMapMethod._throws(Exception.class);
		JVar addToMapMethodParam = addToMapMethod.param(paramType, paramType
				.name().substring(0, 1).toLowerCase()
				+ paramType.name().substring(1, paramType.name().length()));
		JConditional ifAddToMapMethod = addToMapMethod.body()._if(
				mapAttribute.invoke("containsKey")
						.arg(addToMapMethodParam.invoke("getUri")).not());
		ifAddToMapMethod._then().add(
				mapAttribute.invoke("put")
						.arg(addToMapMethodParam.invoke("getUri"))
						.arg(addToMapMethodParam));
		ifAddToMapMethod._else()._throw(
				JExpr._new(this.codeModel.ref(Exception.class)).arg(
						JExpr.lit("Instance already exists")));

		return addToMapMethod;
	}

	public JMethod generateRemoveFromMapMethod(
			final JDefinedClass definedClass, final JFieldVar mapAttribute,
			final JDefinedClass paramType) {
		JMethod removeFromMapMethod = definedClass.method(
				JMod.PUBLIC,
				this.codeModel.VOID,
				"removeFrom"
						+ mapAttribute.name().substring(0, 1).toUpperCase()
						+ mapAttribute.name().substring(1,
								mapAttribute.name().length()));
		removeFromMapMethod._throws(Exception.class);
		// if (instancia.containsKey(i.getUri())) {
		// instancia.remove(i.getUri());
		// } else {
		// throw new Exception("Instance does not exists");
		// }
		JVar removeFromMapMethodParam = removeFromMapMethod.param(
				paramType,
				paramType.name().substring(0, 1).toLowerCase()
						+ paramType.name().substring(1,
								paramType.name().length()));
		JConditional ifRemoveFromMapMethod = removeFromMapMethod.body()._if(
				mapAttribute.invoke("containsKey").arg(
						removeFromMapMethodParam.invoke("getUri")));
		ifRemoveFromMapMethod._then().add(
				mapAttribute.invoke("remove").arg(
						removeFromMapMethodParam.invoke("getUri")));
		ifRemoveFromMapMethod._else()._throw(
				JExpr._new(this.codeModel.ref(Exception.class)).arg(
						JExpr.lit("Instance does not exists")));

		return removeFromMapMethod;
	}

	private void generationMethodsCRUD(final String property,
			final JDefinedClass definedClassA, final JDefinedClass definedClassB) {
		JMethod addMethod = definedClassA.method(JMod.PUBLIC,
				this.codeModel.ref(Boolean.class), "add");
		JVar c = addMethod.param(definedClassB, "c");
		JBlock addMethodBlock = addMethod.body();

		JDefinedClass definedClassFactory = this.codeModel
				._getClass(this.classes.get(definedClassA.name() + "Factory"));

		JTryBlock tryAddMethodBlock = addMethodBlock._try();

		JInvocation inv = addMethodBlock.invoke(JExpr._super(), "getRelation")
				.arg(definedClassFactory.staticRef(definedClassFactory.fields()
						.get(property.toUpperCase())));
		tryAddMethodBlock.body().add(inv.invoke("addToInstanciaMap").arg(c));

		JCatchBlock catchAddMethodBlock = tryAddMethodBlock
				._catch(this.codeModel.ref(Exception.class));
		JVar e = catchAddMethodBlock.param("e");
		catchAddMethodBlock.body().add(e.invoke("printStackTrace"));

		addMethodBlock._return(JExpr.TRUE);
	}

	public static void main(final String[] args) throws Exception {
		/*
		String genericFilePath = "D:/DropBox/Projeto Final/Mobi/Workspace/PacoteFinal/inputMobi/InputLojaVirtualGeneric.txt";
		String completeFilePath = "D:/DropBox/Projeto Final/Mobi/Workspace/PacoteFinal/inputMobi/InputLojaVirtual.txt";

		Mobi mobi = LittleLanguage.carregaDominioLanguage(genericFilePath,
				completeFilePath);

		Mobi2Java mobi2Java = new Mobi2Java(
				mobi,
				"D:/DropBox/Projeto Final/Mobi/Workspace/PacoteFinal/applica��es/MobiTester/src/java");
		mobi2Java.generateAll();
		*/
	}

}
