// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.ReviseWhenPortedToJDK
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.text.MessageFormat
import java.time.Year
import javax.xml.stream.XMLInputFactory
import kotlin.io.path.readText


fun main(args: Array<String>) {
  assert(args.size == 1) { "Should be 1 arg: Path to project" }
  val projectPath = Paths.get(args[0])
  val compatibilityJsonPath = projectPath.resolve("community/plugins/gradle/resources/compatibility/compatibility.json")
  val generatedDataPath = projectPath.resolve("community/plugins/gradle/generated/GradleJvmSupportDefaultData.kt")
  val applicationInfoPath = projectPath.resolve("ultimate/ultimate-resources/resources/idea/ApplicationInfo.xml")
  val applicationVersion = readAppVersion(applicationInfoPath)
  generateJvmSupportMatrices(compatibilityJsonPath, generatedDataPath, applicationVersion)
}

fun createCopyrightComment(): String {
  val year = Year.now().value
  return "// Copyright 2000-$year JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license."
}

fun generateJvmSupportMatrices(json: Path, kt: Path, applicationVersion: String) {
  generateJvmSupportMatrices(json, kt, applicationVersion, createCopyrightComment())
}

fun generateJvmSupportMatrices(json: Path, kt: Path, applicationVersion: String, copyrightComment: String) {
  generateJvmSupportMatrices(json.readText(Charsets.UTF_8), kt, applicationVersion, copyrightComment)
}

fun generateJvmSupportMatrices(jsonData: String, kt: Path, applicationVersion: String, copyrightComment: String) {
  val parsedData = GradleCompatibilityDataParser.parseVersionedJson(jsonData, applicationVersion)
                   ?: throw IllegalStateException("Cannot get compatibility data")
  if ('\n' in copyrightComment) {
    throw IllegalArgumentException("Copyright should be in single line")
  }
  val classFileData = ClassFileData(copyrightComment, parsedData)

  val fileData = getGeneratedString(classFileData)
  kt.toFile().writeText(fileData, Charsets.UTF_8)
}

fun readAppVersion(appInfoPath: Path): String {
  val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()
  BufferedInputStream(FileInputStream(appInfoPath.toFile())).use { bis ->
    val reader = xmlInputFactory.createXMLEventReader(bis)
    val startDocument = reader.nextEvent()
    assert(startDocument.isStartDocument)
    if (reader.hasNext()) {
      val startElement = reader.nextEvent()
      if (!startElement.isStartElement || startElement.asStartElement().name.localPart != "component") {
        throw IllegalArgumentException("${appInfoPath.toFile()} is not ApplicationInfo.xml")
      }
    }
    else throw IllegalArgumentException("${appInfoPath.toFile()} is not ApplicationInfo.xml")


    var fullVersionFormat: String? = null
    var major: String? = null
    var minor: String? = null
    var micro: String? = null
    var patch: String? = null
    var suffix: String? = null
    var eap: Boolean = false

    while (reader.hasNext()) {
      val startElement = reader.nextEvent()
      if (startElement.isStartElement && startElement.asStartElement().name.localPart == "version") {
        val iterator = startElement.asStartElement().attributes
        while (iterator.hasNext()) {
          val attribute = iterator.next()
          when (attribute.name.localPart) {
            "full" -> fullVersionFormat = attribute.value
            "major" -> major = attribute.value
            "minor" -> minor = attribute.value
            "micro" -> micro = attribute.value
            "patch" -> patch = attribute.value
            "eap" -> eap = attribute.value.toBoolean()
            "suffix" -> suffix = attribute.value
          }
        }
        if (suffix == null) {
          suffix = if (eap) " EAP" else ""
        }
        if (fullVersionFormat != null) {
          return MessageFormat.format(fullVersionFormat, major, minor, micro, patch) + suffix
        }
        else {
          return requireNonNullElse(major) + '.' + requireNonNullElse(minor) + suffix
        }
      }
    }
  }
  throw IllegalArgumentException("What a Terrible Failure! ${appInfoPath.toFile()} is not ApplicationInfo.xml")
}

//copy from ApplicationInfoImpl
@ReviseWhenPortedToJDK("9")
private fun requireNonNullElse(s: String?): String {
  return s ?: "0"
}

internal class ClassFileData(
  val copyrightComment: String,
  val parsedData: GradleCompatibilityState
)

internal fun getGeneratedString(data: ClassFileData): String {
  return """
    |${data.copyrightComment}
    |
    |package org.jetbrains.plugins.gradle.jvmcompat;
    |
    |import com.intellij.openapi.application.ApplicationInfo
    |import org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilityState
    |
    |/**
    | * NOTE THIS FILE IS AUTO-GENERATED
    | * DO NOT EDIT IT BY HAND, run "Generate Gradle Compatibility Matrix" configuration instead
    | */
    |internal val DEFAULT_DATA = GradleCompatibilityState(
    |  supportedJavaVersions = listOf(
    ${data.parsedData.supportedJavaVersions.printJavaVersions("|    ")}
    |  ),
    |  supportedGradleVersions = listOf(
    ${data.parsedData.supportedGradleVersions.printGradleVersions("|    ")}
    |  ),
    |  compatibility = listOf(
    ${data.parsedData.compatibility.printCompatibility("|    ")}
    |  )
    |);
  """.trimMargin()
}

private fun List<VersionMapping>.printCompatibility(indentPrefix: String): String {
  return joinToString("," + System.lineSeparator()) {
    buildString {
      append(indentPrefix)
      append("VersionMapping(java = \"")
      append(it.javaVersionInfo)
      append("\", gradle = \"")
      append(it.gradleVersionInfo)
      append("\")")
    }
  }
}

private fun List<String>.printGradleVersions(indentPrefix: String): String {
  return groupBy { it.split(".").first() }.values
    .joinToString("," + System.lineSeparator()) { versions ->
      indentPrefix + versions.joinToString(", ") {
        "\"$it\""
      }
    }
}

private fun List<String>.printJavaVersions(indentPrefix: String): String {
  return indentPrefix + joinToString(", ") {
    "\"$it\""
  }
}
