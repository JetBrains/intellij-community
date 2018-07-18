package org.jetbrains.protocolReader

import gnu.trove.THashMap
import java.nio.file.Paths
import java.util.*

class GenerateConfiguration<ROOT>(val packageName: String, val className: String, readerRootClass: Class<ROOT>, protocolInterfaces: List<Class<*>>, basePackagesMap: Map<Class<*>, String>? = null) {
  val basePackagesMap: List<Map<Class<*>, String>> = if (basePackagesMap == null) listOf<Map<Class<*>, String>>() else listOf(basePackagesMap)

  internal val typeToTypeHandler = InterfaceReader(protocolInterfaces).go()
  internal val root = ReaderRoot(readerRootClass, typeToTypeHandler)
}

fun generate(args: Array<String>, configuration: GenerateConfiguration<*>) {
  val fileUpdater = FileUpdater(Paths.get(parseArgs(args), "${configuration.className}.kt"))
  generate(configuration, fileUpdater.builder)
  fileUpdater.update()
}

private fun parseArgs(args: Array<String>): String {
  val outputDirParam = StringParam()

  val paramMap = HashMap<String, StringParam>(3)
  paramMap.put("output-dir", outputDirParam)

  for (arg in args) {
    if (!arg.startsWith("--")) {
      throw IllegalArgumentException("Unrecognized param: $arg")
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
    val paramListener = paramMap.get(key) ?: throw IllegalArgumentException("Unrecognized param name: $key")
    try {
      paramListener.value = value
    }
    catch (e: IllegalArgumentException) {
      throw IllegalArgumentException("Failed to set value of $key", e)
    }

  }
  for (en in paramMap.entries) {
    if (en.value.value == null) {
      en.value.value = "generated"
    }
  }

  return outputDirParam.value!!
}

private class StringParam {
  var value: String? = null
}

fun buildParserMap(configuration: GenerateConfiguration<*>): Map<Class<*>, String> {
  val fileScope = generate(configuration, StringBuilder())

  val typeToImplClassName = THashMap<Class<*>, String>()
  for (typeWriter in configuration.typeToTypeHandler.values) {
    typeToImplClassName.put(typeWriter!!.typeClass, "${configuration.packageName}.${configuration.className}.${fileScope.getTypeImplShortName(typeWriter)}")
  }
  return typeToImplClassName
}

private fun generate(configuration: GenerateConfiguration<*>, stringBuilder: StringBuilder): FileScope {
  val globalScope = GlobalScope(configuration.typeToTypeHandler.values, configuration.basePackagesMap)
  val fileScope = globalScope.newFileScope(stringBuilder)
  val out = fileScope.output
  out.append("// Generated source")
  out.newLine().append("package ").append(configuration.packageName)
  out.newLine().newLine().append("import org.jetbrains.jsonProtocol.*")

  out.newLine()
  out.newLine().append("import org.jetbrains.io.JsonReaderEx")

  out.newLine().newLine().append("import org.jetbrains.jsonProtocol.JsonReaders.*")
  out.newLine().newLine().append("internal class ").append(configuration.className).space()
  out.append(':').space().append(configuration.root.type.canonicalName).append(if (configuration.root.type.isInterface) "" else "()").openBlock(false)

  val rootClassScope = fileScope.newClassScope()
  configuration.root.write(rootClassScope)

  for (typeWriter in configuration.typeToTypeHandler.values) {
    out.newLine()
    typeWriter!!.write(rootClassScope)
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

    val originName = typeWriter.typeClass.canonicalName
    out.newLine().append("private class ").append(TYPE_FACTORY_NAME_PREFIX).append(globalScope.getTypeImplShortName(typeWriter)).append(" : ObjectFactory<")
    out.append(originName).append(">()").openBlock()
    out.append("override fun read(").append(JSON_READER_PARAMETER_DEF).append("): ").append(originName).append(" = ")
    typeWriter.writeInstantiateCode(rootClassScope, out)
    out.append('(').append(READER_NAME).append(", null)")
    out.closeBlock()
  }

  out.closeBlock()
  return fileScope
}
