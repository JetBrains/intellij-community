// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.psi

import com.intellij.compose.ide.plugin.resources.ComposeResourcesData
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.util.createDocumentBuilder
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

internal suspend fun Project.generateAccessorsFrom(changedComposeResourcesDataSet: Set<ComposeResourcesData>) {
  for (changedComposeResourcesData in changedComposeResourcesDataSet) {
    val composeResourcesDirectoryPath = changedComposeResourcesData.directoryPath
    val dirs = composeResourcesDirectoryPath.listNotHiddenFiles()
    dirs.forEach { dir ->
      if (!Files.isDirectory(dir)) {
        error("${dir.name} is not a directory! Raw files should be placed in the '${composeResourcesDirectoryPath.name}/files' directory.")
      }
    }

    val resources = dirs
      .flatMap { dir ->
        dir.listNotHiddenFiles()
          .mapNotNull { file -> file.toResourceItems(this, composeResourcesDirectoryPath) }
          .flatten()
      }
      .groupBy { it.type }
      .mapValues { (_, items) -> items.groupBy { it.name } }

    // android's ResourceAccessors source root isn't created by default -- in that case we temporarily store them in the ResourceCollectors one
    val resourcesAccessorsDir = readAction { changedComposeResourcesData.accessorsDirectory } ?: return
    val packageOfResClass = changedComposeResourcesData.packageOfResClass
    val moduleDir = resourcesAccessorsDir.findFileByRelativePath(packageOfResClass.replace('.', '/')) ?: return
    val isPublicResClass = changedComposeResourcesData.isPublicResClass
    val nameOfResClass = changedComposeResourcesData.nameOfResClass
    getAccessorsSpecs(
      resources = resources,
      packageName = packageOfResClass,
      sourceSetName = changedComposeResourcesData.accessorsQualifier,
      moduleDir = moduleDir.path,
      isPublicResClass = isPublicResClass,
      nameOfResClass = nameOfResClass,
    )
  }
}

private fun getItemRecord(node: Node): ValueResourceRecord {
  val type = ResourceType.fromString(node.nodeName)
  val key = node.attributes.getNamedItem("name")?.nodeValue ?: error("Attribute 'name' not found.")
  val value: String
  when (type) {
    ResourceType.STRING -> {
      value = handleSpecialCharacters(node.textContent)
    }

    ResourceType.STRING_ARRAY -> {
      val children = node.childNodes
      value = List(children.length) { children.item(it) }
        .filter { it.nodeName == "item" }
        .joinToString(",") { child ->
          handleSpecialCharacters(child.textContent)
        }
    }

    ResourceType.PLURAL_STRING -> {
      val children = node.childNodes
      value = List(children.length) { children.item(it) }
        .filter { it.nodeName == "item" }
        .joinToString(",") { child ->
          val content = handleSpecialCharacters(child.textContent)
          val quantity = child.attributes.getNamedItem("quantity").nodeValue
          quantity.uppercase() + ":" + content
        }
    }
    else -> error("Unknown string resource type: '$type'.")
  }
  return ValueResourceRecord(type, key.asUnderscoredIdentifier(), value)
}

//https://developer.android.com/guide/topics/resources/string-resource#escaping_quotes
/**
 * Replaces
 *
 * '\n' -> new line
 *
 * '\t' -> tab
 *
 * '\uXXXX' -> unicode symbol
 *
 * '\\' -> '\'
 *
 * @param string The input string to handle.
 * @return The string with special characters replaced according to the logic.
 */
private fun handleSpecialCharacters(string: String): String {
  val unicodeNewLineTabRegex = Regex("""\\u[a-fA-F\d]{4}|\\n|\\t""")
  val doubleSlashRegex = Regex("""\\\\""")
  val doubleSlashIndexes = doubleSlashRegex.findAll(string).map { it.range.first }
  val handledString = unicodeNewLineTabRegex.replace(string) { matchResult ->
    if (doubleSlashIndexes.contains(matchResult.range.first - 1)) matchResult.value
    else when (matchResult.value) {
      "\\n" -> "\n"
      "\\t" -> "\t"
      else -> matchResult.value.substring(2).toInt(16).toChar().toString()
    }
  }.replace("""\\""", """\""")
  return handledString
}

internal data class ValueResourceRecord(
  val type: ResourceType,
  val key: String,
  val content: String,
)


private fun Path.listNotHiddenFiles(): List<Path> =
  Files.list(this).filter { !Files.isHidden(it) }.toList()


internal fun String.asUnderscoredIdentifier(): String =
  replace('-', '_')
    .let { if (it.isNotEmpty() && it.first().isDigit()) "_$it" else it }


private suspend fun Path.toResourceItems(
  project: Project,
  relativeTo: Path,
): List<ResourceAccessorItem>? {
  val dirName = this.parent.name
  val typeAndQualifiers = dirName.split("-")
  if (typeAndQualifiers.isEmpty()) return null

  val typeString = typeAndQualifiers.first().lowercase()
  val qualifiers = typeAndQualifiers.takeLast(typeAndQualifiers.size - 1)
  val path = this.relativeTo(relativeTo)


  if (typeString == "string") {
    error("Forbidden directory name '$dirName'! String resources should be declared in 'values/strings.xml'.")
  }

  if (typeString == "files") {
    if (qualifiers.isNotEmpty()) error("The 'files' directory doesn't support qualifiers: '$dirName'.")
    return null
  }

  if (typeString == "values" && this.extension.equals("xml", true)) {
    return getValueResourceItems(project, qualifiers, path)
  }

  val type = ResourceType.fromString(typeString)
  return listOf(ResourceAccessorItem(type, qualifiers, this.nameWithoutExtension.asUnderscoredIdentifier(), path))
}

private suspend fun Path.getValueResourceItems(project: Project, qualifiers: List<String>, path: Path): List<ResourceAccessorItem> {
  // get up-to-date file content
  val fileContent = readAction { toPsiFile(project)?.text } ?: return emptyList()
  val text = InputSource(ByteArrayInputStream(fileContent.toByteArray()))
  // it may throw exceptions if the file is not a valid XML file while the user is typing
  val doc = runCatching { createDocumentBuilder().parse(text) }.getOrNull() ?: return emptyList()
  val items = doc.getElementsByTagName("resources").item(0).childNodes

  val records = List(items.length) { items.item(it) }
    .filter { it.hasAttributes() }
    .map { getItemRecord(it) }

  //check there are no duplicates type and key
  records.groupBy { it.key }
    .filter { it.value.size > 1 }
    .forEach { (key, records) ->
      val allTypes = records.map { it.type }
      if (allTypes.size != allTypes.toSet().size) {
        error("Duplicated key '$key'.")
      }
    }


  return records.map { ResourceAccessorItem(it.type, qualifiers, it.key.asUnderscoredIdentifier(), path) }
}


internal data class ResourceAccessorItem(
  val type: ResourceType,
  val qualifiers: List<String>,
  val name: String,
  val path: Path,
  val offset: Long = -1,
  val size: Long = -1,
)
