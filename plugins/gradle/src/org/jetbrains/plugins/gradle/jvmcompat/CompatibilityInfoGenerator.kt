// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat;

import com.intellij.ReviseWhenPortedToJDK
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.text.MessageFormat
import javax.xml.stream.XMLInputFactory


fun main(args: Array<String>) {
  assert(args.size == 3) { "Should be 3 files: Path to JSON, path to result file dir, path to ApplicationInfo.xml" }
  val applicationVersion = readAppVersion(Paths.get(args[2]))
  generateJvmSupportMatrices(Paths.get(args[0]), Paths.get(args[1]), applicationVersion);
}

fun generateJvmSupportMatrices(json: Path, kt: Path, applicationVersion: String, copyrightComment: String? = null) {

  val jsonData = json.toFile().readText(Charsets.UTF_8)
  val parser = CompatibilityDataParser(applicationVersion)
  val parsedData = parser.parseJson(jsonData) ?: throw IllegalStateException("Cannot get compatibility data")

  if (copyrightComment != null && copyrightComment.indexOf('\n') != -1) throw IllegalArgumentException("Copyright should be in single line")
  val classFileData = ClassFileData(copyrightComment ?: createCopyrightComment(), parsedData)

  val fileData = getGeneratedString(classFileData);
  kt.toFile().writeText(fileData, Charsets.UTF_8)
}

fun createCopyrightComment(): String {
  return "// Copyright 2000-{Year.now().value} JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license."
}

internal class ClassFileData(val copyrightComment: String,
                             val parsedData: CompatibilityData)

fun readAppVersion(appInfoPath: Path): String {
  val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()
  BufferedInputStream(FileInputStream(appInfoPath.toFile())).use { bis ->
    val reader = xmlInputFactory.createXMLEventReader(bis)
    val startDocument = reader.nextEvent();
    assert(startDocument.isStartDocument)
    if (reader.hasNext()) {
      val startElement = reader.nextEvent()
      if (!startElement.isStartElement || startElement.asStartElement().name.localPart != "component") {
        throw IllegalArgumentException("What a Terrible Failure! ${appInfoPath.toFile()} is not ApplicationInfo.xml")
      }
    }
    else throw IllegalArgumentException("What a Terrible Failure! ${appInfoPath.toFile()} is not ApplicationInfo.xml")


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
private fun requireNonNullElse(s: String?): String? {
  return s ?: "0"
}

internal fun getGeneratedString(data: ClassFileData): String {
  return """
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.gradle.jvmcompat;

import com.intellij.openapi.application.ApplicationInfo
import org.jetbrains.plugins.gradle.jvmcompat.CompatibilityData

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate Gradle Compatibility Matrix" configuration instead
 */
 
internal val DEFAULT_DATA = CompatibilityData(
  listOf(
    ${data.parsedData.versionMappings.printAsListData(4, VersionMapping::toConstructor)}
  ),
  listOf(
   ${data.parsedData.supportedJavaVersions.printAsListData(4)}
  ),
  listOf(
   ${data.parsedData.supportedGradleVersions.printAsListData(4)}
  )
);
""".trimIndent()
}

private fun VersionMapping.toConstructor(): String {
  if (comment == null) {
    return "VersionMapping(\"${javaVersionInfo}\", \"${gradleVersionInfo}\")"
  }
  else {
    return "VersionMapping(\"${javaVersionInfo}\", \"${gradleVersionInfo}\", \"${comment}\")"
  }

}

private fun List<String>.printAsListData(indent: Int): String {
  return this.printAsListData(indent) { "\"$it\"" }

}

private fun <T> List<T>.printAsListData(indent: Int, transform: (T) -> String): String {
  return this.joinToString(
    separator = "," + System.lineSeparator() + " ".repeat(indent),
    transform = transform
  )
}
