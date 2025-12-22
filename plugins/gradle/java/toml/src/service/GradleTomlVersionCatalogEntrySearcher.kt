// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.toml.service

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogEntrySearcher
import org.jetbrains.plugins.gradle.service.resolve.VersionCatalogEntry
import org.jetbrains.plugins.gradle.toml.MatchingType.EXACT
import org.jetbrains.plugins.gradle.toml.MatchingType.STARTS_WITH
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlTable

@ApiStatus.Internal
class GradleTomlVersionCatalogEntrySearcher : GradleVersionCatalogEntrySearcher {

  /**
   * Given a [TomlFile] and a path, returns the corresponding key element.
   * For example, given "versions.foo", it will locate the `foo =` key/value
   * pair under the `\[versions]` table and return it. As a special case,
   * `libraries` don't have to be explicitly named in the path.
   */
  override fun findEntryElement(versionCatalog: PsiFile, entryPath: String): PsiElement? {
    if (versionCatalog !is TomlFile) return null

    val (section, lookupKey) = getLookupSectionAndKey(entryPath)
    // At the root level, look for the right section (versions, libraries, plugins, bundles)
    versionCatalog.children.forEach { element ->
      // [table]
      // alias =
      if (element is TomlTable) {
        val tableName = element.header.key?.text
        if (tableName == section) {
          return findAlias(element, lookupKey)
        }
      }
      // for corner cases, when the section is not declared as [table]
      if (element is TomlKeyValue) {
        val keyText = element.key.text
        // libraries.alias = ""
        if (keysMatch(keyText, "$section.$lookupKey")) {
          return element
        }
        // libraries = { alias = ""
        else if (element.value is TomlInlineTable && keyText == section) {
          return findAlias(element.value as TomlInlineTable, lookupKey)
        }
      }
    }
    return null
  }

  override fun findEntriesMatching(
    versionCatalog: PsiFile,
    entrySearchString: String,
  ): List<VersionCatalogEntry> {
    if (versionCatalog !is TomlFile) return emptyList()

    val matchingType = STARTS_WITH
    val result = mutableListOf<VersionCatalogEntry>()
    val (section, lookupKey) = getLookupSectionAndKey(entrySearchString)
    // At the root level, look for the right section (versions, libraries, plugins, bundles)
    versionCatalog.children.forEach { element ->
      // [table]
      // alias =
      if (element is TomlTable) {
        val tableName = element.header.key?.text
        if (tableName == section) {
          val entries = findAliases(element, lookupKey, matchingType)
            .map { entryElement -> createCatalogEntry(section, entryElement.key.text) }
          result.addAll(entries)
        }
      }
      // for corner cases, when the section is not declared as [table]
      if (element is TomlKeyValue) {
        val keyText = element.key.text
        // libraries.alias = ""
        if (keysMatch(keyText, "$section.$lookupKey", matchingType)) {
          val entry = createCatalogEntry(section = keyText.substringBefore('.'),
                                         key = keyText.substringAfter('.'))
          result.add(entry)
        }
        // libraries = { alias = ""
        else if (element.value is TomlInlineTable && keyText == section) {
          val entries = findAliases(element.value as TomlInlineTable, lookupKey, matchingType)
            .map { entryElement -> createCatalogEntry(section, key = entryElement.key.text) }
          result.addAll(entries)
        }
      }
    }
    return result
  }
}

private data class TomlCatalogEntry(override val pathForBuildScript: String) : VersionCatalogEntry

private fun createCatalogEntry(section: String, key: String): TomlCatalogEntry {
  val normalizedKey = normalizeTomlKey(key)
  val buildScriptPath = when (section) {
    "libraries" -> normalizedKey
    else -> "$section.$normalizedKey"
  }
  return TomlCatalogEntry(buildScriptPath)
}

private fun getLookupSectionAndKey(entryPath: String): Pair<String, String> {
  val sectionPrefixes = listOf("versions.", "bundles.", "plugins.")
  return if (sectionPrefixes.none { entryPath.startsWith(it) }) {
    "libraries" to entryPath
  }
  else {
    entryPath.substringBefore('.') to entryPath.substringAfter('.')
  }
}

private fun findAlias(valueOwner: TomlKeyValueOwner, target: String): PsiElement? {
  for (entry in valueOwner.entries) {
    val entryKeyText = entry.key.text
    if (keysMatch(entryKeyText, target)) {
      return entry
    }
  }
  return null
}

private fun findAliases(table: TomlKeyValueOwner, lookupKey: String, matchingType: MatchingType): List<TomlKeyValue> =
  table.entries.filter {
    keysMatch(it.key.text, lookupKey, matchingType)
  }

private enum class MatchingType { EXACT, STARTS_WITH }

private fun keysMatch(keyText: String, lookupKey: String, matchingType: MatchingType = EXACT): Boolean {
  if (keyText.length != lookupKey.length && matchingType == EXACT) {
    return false
  }
  val normalizedTomlKey = normalizeTomlKey(keyText)
  val normalizedLookupKey = getKeyParts(lookupKey).joinToString(".")
  return when (matchingType) {
    EXACT -> normalizedTomlKey == normalizedLookupKey
    STARTS_WITH -> normalizedTomlKey.startsWith(normalizedLookupKey)
  }
}

/**
 * In a TOML version catalog, a first char after a separator is case-insensitive.
 * For example, both `junit-jupiter` and `junit-Jupiter` could match the same reference in a build script - `libs.junit.jupiter`.
 */
private fun normalizeTomlKey(tomlKey: String): String =
  getKeyParts(tomlKey).joinToString(".") { part ->
    part.replaceFirstChar { it.lowercaseChar() }
  }

private fun getKeyParts(keyText: String): List<String> = keyText.split('-', '_', '.')