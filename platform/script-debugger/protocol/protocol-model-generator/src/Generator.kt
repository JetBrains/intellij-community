package org.jetbrains.protocolModelGenerator

import gnu.trove.THashMap
import org.jetbrains.jsonProtocol.*
import org.jetbrains.protocolReader.FileUpdater
import org.jetbrains.protocolReader.TextOutput
import java.nio.file.FileSystems
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet

/**
 * Read metamodel and generates set of files with Java classes/interfaces for the protocol.
 */
class Generator(outputDir: String, rootPackage: String, requestClassName: String) {
  val jsonProtocolParserClassNames = ArrayList<String>()
  val parserRootInterfaceItems = ArrayList<ParserRootInterfaceItem>()
  val typeMap = TypeMap()

  val nestedTypeMap = THashMap<NamePath, StandaloneType>()

  private val fileSet: FileSet
  val naming: Naming

  init {
    fileSet = FileSet(FileSystems.getDefault().getPath(outputDir))
    naming = Naming(rootPackage, requestClassName)
  }

  public class Naming(val inputPackage: String, val requestClassName: String) {
    public val params: ClassNameScheme
    public val additionalParam: ClassNameScheme
    public val outputTypedef: ClassNameScheme

    public val commandResult: ClassNameScheme.Input
    public val eventData: ClassNameScheme.Input
    public val inputValue: ClassNameScheme
    public val inputEnum: ClassNameScheme
    public val inputTypedef: ClassNameScheme

    public val commonTypedef: ClassNameScheme

    init {
      params = ClassNameScheme.Output("", inputPackage)
      additionalParam = ClassNameScheme.Output("", inputPackage)
      outputTypedef = ClassNameScheme.Output("Typedef", inputPackage)
      commonTypedef = ClassNameScheme.Common("Typedef", inputPackage)
      commandResult = ClassNameScheme.Input("Result", inputPackage)
      eventData = ClassNameScheme.Input("EventData", inputPackage)
      inputValue = ClassNameScheme.Input("Value", inputPackage)
      inputEnum = ClassNameScheme.Input("", inputPackage)
      inputTypedef = ClassNameScheme.Input("Typedef", inputPackage)
    }
  }

  fun go(metamodel: ProtocolMetaModel.Root) {
    initializeKnownTypes()

    val domainList = metamodel.domains()
    val domainGeneratorMap = HashMap<String, DomainGenerator>()
    for (domain in domainList) {
      if (isDomainSkipped(domain)) {
        System.out.println("Domain skipped: " + domain.domain())
        continue
      }
      val domainGenerator = DomainGenerator(this, domain)
      domainGeneratorMap.put(domain.domain(), domainGenerator)
      domainGenerator.registerTypes()
    }

    for (domain in domainList) {
      if (!isDomainSkipped(domain)) {
        System.out.println("Domain generated: " + domain.domain())
      }
    }

    typeMap.domainGeneratorMap = domainGeneratorMap

    for (domainGenerator in domainGeneratorMap.values()) {
      domainGenerator.generateCommandsAndEvents()
    }

    typeMap.generateRequestedTypes()
    generateParserInterfaceList()
    generateParserRoot(parserRootInterfaceItems)
    fileSet.deleteOtherFiles()
  }

  fun resolveType(typedObject: ItemDescriptor, scope: ResolveAndGenerateScope): TypeDescriptor {
    val optional = typedObject is ItemDescriptor.Named && typedObject.optional()
    return switchByType(typedObject, object : TypeVisitor<TypeDescriptor> {
      override fun visitRef(refName: String): TypeDescriptor {
        return TypeDescriptor(resolveRefType(scope.getDomainName(), refName, scope.getTypeDirection()), optional)
      }

      override fun visitBoolean(): TypeDescriptor {
        return TypeDescriptor(BoxableType.BOOLEAN, optional)
      }

      override fun visitEnum(enumConstants: List<String>): TypeDescriptor {
        assert(scope is MemberScope)
        return TypeDescriptor((scope as MemberScope).generateEnum(typedObject.description(), enumConstants), optional)
      }

      override fun visitString(): TypeDescriptor {
        return TypeDescriptor(BoxableType.STRING, optional)
      }

      override fun visitInteger(): TypeDescriptor {
        return TypeDescriptor(BoxableType.INT, optional)
      }

      override fun visitNumber(): TypeDescriptor {
        return TypeDescriptor(BoxableType.NUMBER, optional)
      }

      override fun visitMap(): TypeDescriptor {
        return TypeDescriptor(BoxableType.MAP, optional)
      }

      override fun visitArray(items: ProtocolMetaModel.ArrayItemType): TypeDescriptor {
        val type = scope.resolveType(items).type
        return TypeDescriptor(ListType(type), optional, false, type == BoxableType.ANY_STRING)
      }

      override fun visitObject(properties: List<ProtocolMetaModel.ObjectProperty>?): TypeDescriptor {
        return TypeDescriptor(scope.generateNestedObject(typedObject.description(), properties), optional)
      }

      override fun visitUnknown(): TypeDescriptor {
        return TypeDescriptor(BoxableType.STRING, optional, false, true)
      }
    })
  }

  private fun generateParserInterfaceList() {
    val fileUpdater = startJavaFile(naming.inputPackage, PARSER_INTERFACE_LIST_CLASS_NAME + ".java")
    // Write classes in stable order.
    Collections.sort<String>(jsonProtocolParserClassNames)

    val out = fileUpdater.out
    out.append("public class ").append(PARSER_INTERFACE_LIST_CLASS_NAME).openBlock()
    out.append("public static final Class<?>[] LIST =").openBlock()
    for (name in jsonProtocolParserClassNames) {
      out.append(name).append(".class,").newLine()
    }
    out.closeBlock()
    out.semi()
    out.closeBlock()
    fileUpdater.update()
  }

  private fun generateParserRoot(parserRootInterfaceItems: List<ParserRootInterfaceItem>) {
    val fileUpdater = startJavaFile(naming.inputPackage, READER_INTERFACE_NAME + ".java")
    // Write classes in stable order.
    Collections.sort<ParserRootInterfaceItem>(parserRootInterfaceItems)

    val out = fileUpdater.out
    out.append("public abstract class ").append(READER_INTERFACE_NAME).space().append("implements org.jetbrains.jsonProtocol.ResponseResultReader").openBlock()
    for (item in parserRootInterfaceItems) {
      item.writeCode(out)
    }
    out.newLine().newLine().append("@Override").newLine().append("public Object readResult(String methodName, org.jetbrains.io.JsonReaderEx reader)")
    out.openBlock()

    var isNotFirst = false
    for (item in parserRootInterfaceItems) {
      if (isNotFirst) {
        out.append("else ")
      }
      else {
        isNotFirst = true
      }
      out.append("if (methodName.equals(\"")
      if (!item.domain.isEmpty()) {
        out.append(item.domain).append('.')
      }
      out.append(item.name).append('"').append(")) return ")
      item.appendReadMethodName(out)
      out.append("(reader)").semi().newLine()
    }
    out.append("else return null").semi()
    out.closeBlock()

    out.closeBlock()
    fileUpdater.update()
  }

  /**
   * Resolve absolute (DOMAIN.TYPE) or relative (TYPE) type name
   */
  private fun resolveRefType(scopeDomainName: String, refName: String, direction: TypeData.Direction): BoxableType {
    val pos = refName.indexOf('.')
    val domainName: String
    val shortName: String
    if (pos == -1) {
      domainName = scopeDomainName
      shortName = refName
    }
    else {
      domainName = refName.substring(0, pos)
      shortName = refName.substring(pos + 1)
    }
    return typeMap.resolve(domainName, shortName, direction)!!
  }

  fun startJavaFile(nameScheme: ClassNameScheme, domain: ProtocolMetaModel.Domain, baseName: String): FileUpdater {
    return startJavaFile(nameScheme.getPackageNameVirtual(domain.domain()), nameScheme.getShortName(baseName) + ".java")
  }

  public fun startJavaFile(packageName: String, filename: String): FileUpdater {
    val fileUpdater = fileSet.createFileUpdater(packageName.replace('.', '/') + '/' + filename)
    fileUpdater.out.append("// Generated source").newLine().append("package ").append(packageName).semi().newLine().newLine()
    return fileUpdater
  }
}

private val PARSER_INTERFACE_LIST_CLASS_NAME = "GeneratedReaderInterfaceList"
val READER_INTERFACE_NAME = "ProtocolResponseReader"

private fun isDomainSkipped(domain: ProtocolMetaModel.Domain): Boolean {
  if (domain.domain() == "CSS" || domain.domain() == "Inspector") {
    return false
  }

  // todo DOMDebugger
  return domain.hidden() || domain.domain() == "DOMDebugger" || domain.domain() == "Timeline" || domain.domain() == "Input"
}

fun generateMethodNameSubstitute(originalName: String, out: TextOutput): String {
  if (!BAD_METHOD_NAMES.contains(originalName)) {
    return originalName
  }
  out.append("@org.jetbrains.jsonProtocol.JsonField(name = \"").append(originalName).append("\")").newLine()
  return "get" + Character.toUpperCase(originalName.charAt(0)) + originalName.substring(1)
}

fun capitalizeFirstChar(s: String): String {
  if (!s.isEmpty() && Character.isLowerCase(s.charAt(0))) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1)
  }
  return s
}

fun <R> switchByType(typedObject: ItemDescriptor, visitor: TypeVisitor<R>): R {
  val refName = if (typedObject is ItemDescriptor.Referenceable) typedObject.ref() else null
  if (refName != null) {
    return visitor.visitRef(refName)
  }
  val typeName = typedObject.type()
  when (typeName) {
    BOOLEAN_TYPE -> return visitor.visitBoolean()
    STRING_TYPE -> {
      if (typedObject.getEnum() != null) {
        return visitor.visitEnum(typedObject.getEnum()!!)
      }
      return visitor.visitString()
    }
    INTEGER_TYPE, "int" -> return visitor.visitInteger()
    NUMBER_TYPE -> return visitor.visitNumber()
    ARRAY_TYPE -> return visitor.visitArray(typedObject.items())
    OBJECT_TYPE -> {
      if (typedObject !is ItemDescriptor.Type) {
        return visitor.visitObject(null)
      }

      val properties = typedObject.properties()
      if (properties == null || properties.isEmpty()) {
        return visitor.visitMap()
      }
      else {
        return visitor.visitObject(properties)
      }
    }
    ANY_TYPE -> return visitor.visitUnknown()
    UNKNOWN_TYPE -> return visitor.visitUnknown()
  }
  throw RuntimeException("Unrecognized type " + typeName)
}

private fun initializeKnownTypes() {
  // Code example:
  // typeMap.getTypeData("Page", "Cookie").getInput().setJavaTypeName("Object");
}

private val BAD_METHOD_NAMES = HashSet(listOf("this"))