// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.toml.service

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogEntrySearcher
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
  ): List<PsiElement> {
    if (versionCatalog !is TomlFile) return emptyList()

    val matchingType = STARTS_WITH
    val result = mutableListOf<PsiElement>()
    val (section, lookupKey) = getLookupSectionAndKey(entrySearchString)
    // At the root level, look for the right section (versions, libraries, plugins, bundles)
    versionCatalog.children.forEach { element ->
      // [table]
      // alias =
      if (element is TomlTable) {
        val tableName = element.header.key?.text
        if (tableName == section) {
          result.addAll(findAliases(element, lookupKey, matchingType))
        }
      }
      // for corner cases, when the section is not declared as [table]
      if (element is TomlKeyValue) {
        val keyText = element.key.text
        // libraries.alias = ""
        if (keysMatch(keyText, "$section.$lookupKey", matchingType)) {
          result.add(element)
        }
        // libraries = { alias = ""
        else if (element.value is TomlInlineTable && keyText == section) {
          result.addAll(findAliases(element.value as TomlInlineTable, lookupKey, matchingType))
        }
      }
    }
    return result
  }
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

private fun findAliases(table: TomlKeyValueOwner, lookupKey: String, matchingType: MatchingType): List<PsiElement> =
  table.entries.filter {
    keysMatch(it.key.text, lookupKey, matchingType)
  }

private enum class MatchingType { EXACT, STARTS_WITH }

private fun keysMatch(keyText: String, lookupKey: String, matchingType: MatchingType = EXACT): Boolean {
  if (keyText.length != lookupKey.length && matchingType == EXACT) {
    return false
  }

  val tomlKeyParts = getKeyParts(keyText)
    // The first character may be capital after `-_.` symbols in TOML
    // it still makes it equal to low-case reference - Gradle implementation detail
    .map { part -> part.replaceFirstChar { it.lowercaseChar() } }

  val lookupKeyParts = getKeyParts(lookupKey)
  return when (matchingType) {
    EXACT -> tomlKeyParts == lookupKeyParts
    STARTS_WITH -> tomlKeyParts.joinToString(".").startsWith(lookupKeyParts.joinToString("."))
  }
}

private fun getKeyParts(keyText: String): List<String> = keyText.split('-', '_', '.')