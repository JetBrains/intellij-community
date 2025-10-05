// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.scripts

import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.io.Writer
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.system.exitProcess

/**
 * Generates a Kotlin class from your .properties file. This file is necessary for non-JVM targets where Java PropertyResourceBundles are not available.
 */
fun main(args: Array<String>) {
  if (args.size != 3) {
    println("""
      |Please pass 3 arguments:
      |  1. the path to .properties file
      |  2. the path to the output file as arguments
      |  3. the package name to use
      |""".trimMargin())
    exitProcess(-1)
  }

  generateMapping(
    properties = args[0],
    output = args[1],
    packageName = args[2],
  )
}

fun generateMapping(
  properties: String,
  output: String,
  packageName: String,
) {
  val propertiesPath = Path(properties)
  if (!propertiesPath.exists()) {
    println(".properties file does not exist! Path: $propertiesPath")
    exitProcess(-1)
  }

  val outputPath = Path(output)
  if (!outputPath.exists()) {
    outputPath.createParentDirectories()
    outputPath.createFile()
  }

  val mappings = loadMappingsFromFile(propertiesPath)

  val fileName = outputPath.nameWithoutExtension
  outputPath.writer().buffered().use { writer ->
    writer.printClass(fileName, packageName, mappings, "<full path to ${propertiesPath.name}> <full path to ${outputPath.name}> $packageName")
  }

  println("Done! Result: $outputPath")
}

/**
 * Use to ensure the properties file and the default resources file are in sync.
 */
fun assertPropertiesMatch(
  propertiesFileName: String,
  defaultResourcesFileName: String,
  classLoader: ClassLoader,
  actualMapping: Map<String, String>,
) {
  val propResourceName = propertiesFileName.asResourceName()
  val propertiesFileStream = classLoader.getResourceAsStream(propResourceName)!!
  val expectedMapping = propertiesFileStream.use { stream ->
    loadPropertiesBundleMappings(stream)
  }

  require(expectedMapping == actualMapping) {
    """
          |$propertiesFileName and $defaultResourcesFileName do not match.
          |
          |Please regenerate $defaultResourcesFileName. See instruction how to do it in the class's doc.
    """.trimMargin()
  }
}

private fun Writer.printClass(
  className: String,
  packageName: String,
  mappings: Map<String, String>,
  parametersString: String,
) {
  appendLine("package $packageName")
  appendLine()
  appendLine(getRegenerationInstruction(parametersString).addLinePrefix("// "))
  appendLine("internal object $className {")
  appendLine("  val mappings: Map<String, String> = mapOf(")
  for ((key, value) in mappings.entries.sortedBy { it.key }) {
    val escapedValue = value
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    appendLine("    \"$key\" to \"$escapedValue\",")
  }
  appendLine("  )")
  appendLine('}')
}

private fun getRegenerationInstruction(parametersString: String): String {
  return """
    To regenerate the file, please run `GenerateBundleMapping` run configuration with the following parameters: ")
      $parametersString
  """.trimIndent()
}

private fun String.addLinePrefix(linePrefix: String): String = split('\n').joinToString("\n") { "$linePrefix$it" }

private fun loadMappingsFromFile(propertiesPath: Path): Map<String, String> {
  propertiesPath.inputStream().buffered().use { stream ->
    return loadPropertiesBundleMappings(stream)
  }
}

private fun loadPropertiesBundleMappings(stream: InputStream): Map<String, String> {
  val properties = Properties()
  properties.load(stream)
  return properties.entries.associate { (key, value) ->
    (key as String) to (value as String)
  }
}

private fun String.asResourceName(): String = object : ResourceBundle.Control() {}.toResourceName(this, "properties")
