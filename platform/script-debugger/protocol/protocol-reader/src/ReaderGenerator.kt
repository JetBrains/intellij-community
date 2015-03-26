package org.jetbrains.protocolReader

import gnu.trove.THashMap
import java.io.File
import java.nio.file.FileSystems
import java.util.HashMap
import java.util.LinkedHashMap

public class GenerateConfiguration<ROOT>(val packageName: String, val className: String, readerRootClass: Class<ROOT>, protocolInterfaces: Array<Class<*>>, basePackagesMap: Map<Class<*>, String>? = null) {
  val basePackagesMap: Collection<Map<Class<*>, String>>

  val typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>>
  val root: ReaderRoot<ROOT>

  init {
    this.basePackagesMap = if (basePackagesMap == null) listOf<Map<Class<*>, String>>() else listOf(basePackagesMap)
    typeToTypeHandler = InterfaceReader(protocolInterfaces).go()
    root = ReaderRoot(readerRootClass, typeToTypeHandler)
  }
}

public fun generate(args: Array<String>, configuration: GenerateConfiguration<*>) {
  val fileUpdater = FileUpdater(FileSystems.getDefault().getPath(parseArgs(args), configuration.packageName.replace('.', File.separatorChar), configuration.className + ".java"))
  generate(configuration, fileUpdater.builder)
  fileUpdater.update()
}

private fun parseArgs(args: Array<String>): String {
  val outputDirParam = StringParam()

  val paramMap = HashMap<String, StringParam>(3)
  paramMap.put("output-dir", outputDirParam)

  for (arg in args) {
    if (!arg.startsWith("--")) {
      throw IllegalArgumentException("Unrecognized param: " + arg)
    }
    val equalsPos = arg.indexOf('=', 2)
    val key: String
    val value: String?
    if (equalsPos == -1) {
      key = arg.substring(2).trim()
      value = null
    }
    else {
      key = arg.substring(2, equalsPos).trim()
      value = arg.substring(equalsPos + 1).trim()
    }
    val paramListener = paramMap.get(key)
    if (paramListener == null) {
      throw IllegalArgumentException("Unrecognized param name: " + key)
    }
    try {
      paramListener.value = value
    }
    catch (e: IllegalArgumentException) {
      throw IllegalArgumentException("Failed to set value of " + key, e)
    }

  }
  for (en in paramMap.entrySet()) {
    if (en.getValue().value == null) {
      en.getValue().value = "generated"
    }
  }

  return outputDirParam.value!!
}

private class StringParam {
  public var value: String? = null
}

public fun buildParserMap(configuration: GenerateConfiguration<*>): Map<Class<*>, String> {
  val fileScope = generate(configuration, StringBuilder())

  val typeToImplClassName = THashMap<Class<*>, String>()
  for (typeWriter in configuration.typeToTypeHandler.values()) {
    typeToImplClassName.put(typeWriter.typeClass, configuration.packageName + "." + configuration.className + "." + fileScope.getTypeImplShortName(typeWriter))
  }
  return typeToImplClassName
}

private fun generate(configuration: GenerateConfiguration<*>, stringBuilder: StringBuilder): FileScope {
  val globalScope = GlobalScope(configuration.typeToTypeHandler.values(), configuration.basePackagesMap)
  val fileScope = globalScope.newFileScope(stringBuilder)
  val out = fileScope.output
  out.append("// Generated source")
  out.newLine().append("package ").append(configuration.packageName).append(';')
  out.newLine().newLine().append("import org.jetbrains.jsonProtocol.*;")
  out.newLine().newLine().append("import org.jetbrains.annotations.NotNull;")
  out.newLine().newLine().append("import static org.jetbrains.jsonProtocol.JsonReaders.*;")
  out.newLine().newLine().append("public final class ").append(configuration.className).space()
  out.append(if (configuration.root.type.isInterface()) "implements" else "extends").space().append(configuration.root.type.getCanonicalName()).openBlock(false)

  val rootClassScope = fileScope.newClassScope()
  configuration.root.writeStaticMethodJava(rootClassScope)

  for (typeWriter in configuration.typeToTypeHandler.values()) {
    out.newLine()
    typeWriter.write(rootClassScope)
    out.newLine()
  }

  var isFirst = true
  for (typeWriter in globalScope.getTypeFactories()) {
    if (isFirst) {
      isFirst = false
    }
    else {
      out.newLine()
    }

    val originName = typeWriter.typeClass.getCanonicalName()
    out.newLine().append("private static final class ").append(globalScope.getTypeImplShortName(typeWriter)).append(TYPE_FACTORY_NAME_POSTFIX).append(" extends ObjectFactory<")
    out.append(originName).append('>').openBlock()
    out.append("@Override").newLine().append("public ").append(originName).append(" read(").append(JSON_READER_PARAMETER_DEF)
    out.append(')').openBlock()
    out.append("return ")
    typeWriter.writeInstantiateCode(rootClassScope, out)
    out.append('(').append(READER_NAME).append(", null);").closeBlock()
    out.closeBlock()
  }

  out.closeBlock()
  return fileScope
}
