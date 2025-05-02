// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.psi

import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private const val ITEMS_PER_FILE_LIMIT = 100
private const val RESOURCE_ITEM_CLASS = "ResourceItem"
private const val LANGUAGE_QUALIFIER = "LanguageQualifier"
private const val REGION_QUALIFIER = "RegionQualifier"
private const val THEME_QUALIFIER = "ThemeQualifier"
private const val DENSITY_QUALIFIER = "DensityQualifier"
private const val INTERNAL_RESOURCE_API = "InternalResourceApi"
private const val COMPOSE_RESOURCES_FQN = "org.jetbrains.compose.resources"

internal suspend fun getAccessorsSpecs(
  //type -> id -> items
  resources: Map<ResourceType, Map<String, List<ResourceAccessorItem>>>,
  packageName: String,
  sourceSetName: String,
  moduleDir: String,
) {


  //we need to sort it to generate the same code on different platforms
  // todo[alexandru.resiga] this can lead to "file cache conflict" while renaming
  sortResources(resources).forEach { (type, idToResources) ->
    val chunks = idToResources.keys.chunked(ITEMS_PER_FILE_LIMIT)

    chunks.forEachIndexed { index, ids ->
      getChunkFileSpec(
        type,
        type.accessorName.uppercaseFirstChar() + index + "." + sourceSetName + ".kt",
        packageName,
        moduleDir,
        idToResources.subMap(ids.first(), true, ids.last(), true)
      )
    }
  }

}

private suspend fun getChunkFileSpec(
  type: ResourceType,
  fileName: String,
  packageName: String,
  moduleDir: String,
  idToResources: Map<String, List<ResourceAccessorItem>>,
) {
  val content = buildString {
    append("""
        @file:OptIn($INTERNAL_RESOURCE_API::class)

        package $packageName

        import kotlin.OptIn
        import $COMPOSE_RESOURCES_FQN.$INTERNAL_RESOURCE_API
        import $COMPOSE_RESOURCES_FQN.${type.resourceName}
        import $COMPOSE_RESOURCES_FQN.$RESOURCE_ITEM_CLASS
        import $COMPOSE_RESOURCES_FQN.$LANGUAGE_QUALIFIER
        import $COMPOSE_RESOURCES_FQN.$REGION_QUALIFIER
        import $COMPOSE_RESOURCES_FQN.$THEME_QUALIFIER
        import $COMPOSE_RESOURCES_FQN.$DENSITY_QUALIFIER
        
      """.trimIndent())
    appendLine()
    idToResources.forEach { (resName, items) ->
      appendLine(
        buildString {
          append("internal val Res.${type.accessorName}.${resName}: ${type.resourceName} by lazy { ")
          append("${type.resourceName}(\"${type.typeName}:${resName}\", ${if (type.isStringType) "\"$resName\", " else ""}")
          append(items.joinToString(prefix = "setOf(", postfix = ")) }") { item ->
            buildString {
              append("${RESOURCE_ITEM_CLASS}(")
              append("setOf(${item.addQualifiers()}),")
              append("\"${item.path}\",")
              append("${item.offset},")
              append(item.size)
              append(")")
            }
          })
        }
      )
    }
  }

  writeAccessors(moduleDir, fileName, content)
}

/**
 * Writes the specified content to a file within the given module directory. This operation is performed
 * under a write lock and includes file system updates.
 *
 * @param moduleDir the directory path of the module where the file should be written
 * @param fileName the name of the file to write the content to
 * @param content the content to be written into the specified file
 */
@RequiresWriteLock
private suspend fun writeAccessors(moduleDir: String, fileName: String, content: String): Unit = writeAction {
  val path = Path.of(moduleDir, fileName)
  runUndoTransparentWriteAction {
    Files.writeString(path, content)
  }

  path.toVirtualFile()?.let { virtualFile ->
    virtualFile.refresh(false, false)
    FileDocumentManager.getInstance().reloadFiles(virtualFile)
  }
}


private fun sortResources(
  resources: Map<ResourceType, Map<String, List<ResourceAccessorItem>>>,
): TreeMap<ResourceType, TreeMap<String, List<ResourceAccessorItem>>> {
  val result = TreeMap<ResourceType, TreeMap<String, List<ResourceAccessorItem>>>()
  resources
    .entries
    .forEach { (type, items) ->
      val typeResult = TreeMap<String, List<ResourceAccessorItem>>()
      items
        .entries
        .forEach { (name, resItems) ->
          typeResult[name] = resItems.sortedBy { it.name }
        }
      result[type] = typeResult
    }
  return result
}


internal fun String.uppercaseFirstChar(): String =
  transformFirstCharIfNeeded(
    shouldTransform = { it.isLowerCase() },
    transform = { it.uppercaseChar() }
  )

private inline fun String.transformFirstCharIfNeeded(
  shouldTransform: (Char) -> Boolean,
  transform: (Char) -> Char,
): String {
  if (isNotEmpty()) {
    val firstChar = this[0]
    if (shouldTransform(firstChar)) {
      val sb = StringBuilder(length)
      sb.append(transform(firstChar))
      sb.append(this, 1, length)
      return sb.toString()
    }
  }
  return this
}

private fun ResourceAccessorItem.addQualifiers(): String = buildString {
  val languageRegex = Regex("[a-z]{2,3}")
  val regionRegex = Regex("r[A-Z]{2}")

  val qualifiersMap = mutableMapOf<String, String>()

  fun saveQualifier(className: String, qualifier: String) {
    qualifiersMap[className]?.let {
      error("${path} contains repetitive qualifiers: '$it' and '$qualifier'.")
    }
    qualifiersMap[className] = qualifier
  }

  qualifiers.forEach { q ->
    when (q) {
      "light",
      "dark",
        -> {
        saveQualifier(THEME_QUALIFIER, q)
      }

      "mdpi",
      "hdpi",
      "xhdpi",
      "xxhdpi",
      "xxxhdpi",
      "ldpi",
        -> {
        saveQualifier(DENSITY_QUALIFIER, q)
      }

      else -> when {
        q.matches(languageRegex) -> {
          saveQualifier(LANGUAGE_QUALIFIER, q)
        }

        q.matches(regionRegex) -> {
          saveQualifier(REGION_QUALIFIER, q)
        }

        else -> error("${path} contains unknown qualifier: '$q'.")
      }
    }
  }
  qualifiersMap[THEME_QUALIFIER]?.let { q -> append("$THEME_QUALIFIER.${q.uppercase()}, ") }
  qualifiersMap[DENSITY_QUALIFIER]?.let { q -> append("$DENSITY_QUALIFIER.${q.uppercase()}, ") }
  qualifiersMap[LANGUAGE_QUALIFIER]?.let { q -> append("$LANGUAGE_QUALIFIER(\"$q\"), ") }
  qualifiersMap[REGION_QUALIFIER]?.let { q ->
    val lang = qualifiersMap[LANGUAGE_QUALIFIER]
    if (lang == null) {
      error("Region qualifier must be used only with language.\nFile: ${path}")
    }
    val langAndRegion = "$lang-$q"
    if (!path.toString().contains("-$langAndRegion")) {
      error("Region qualifier must be declared after language: '$langAndRegion'.\nFile: ${path}")
    }
    append("$REGION_QUALIFIER(\"${q.takeLast(2)}\"), ")
  }
}