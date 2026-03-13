// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.toml.service

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogEntrySearcher
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner

@ApiStatus.Internal
class GradleTomlVersionCatalogEntrySearcher: GradleVersionCatalogEntrySearcher {

  /**
   * Given a [TomlFile] and a path, returns the corresponding key element.
   * For example, given "versions.foo", it will locate the `foo =` key/value
   * pair under the `\[versions]` table and return it. As a special case,
   * `libraries` don't have to be explicitly named in the path.
   */
  override fun findEntryElement(versionCatalog: PsiFile, entryPath: String): PsiElement? {
    if (versionCatalog !is TomlFile) return null

    val prefix = listOf("versions.", "bundles.", "plugins.")
    val section: String
    val target: String
    if (prefix.none { entryPath.startsWith(it) }) {
      section = "libraries"
      target = entryPath
    }
    else {
      section = entryPath.substringBefore('.')
      target = entryPath.substringAfter('.')
    }
    // At the root level, look for the right section (versions, libraries, etc)
    versionCatalog.children.forEach { element ->
      // [table]
      // alias =
      if (element is TomlHeaderOwner) {
        val keyText = element.header.key?.text
        if (keysMatch(keyText, section)) {
          if (element is TomlKeyValueOwner) {
            return findAlias(element, target)
          }
        }
      }
      // for corner cases
      if (element is TomlKeyValue) {
        val keyText = element.key.text
        // libraries.alias = ""
        if (keysMatch(keyText, "$section.$target")) {
          return element
        }
        else
        // libraries = { alias = ""
          if (element.value is TomlInlineTable && keysMatch(keyText, section)) {
            return findAlias(element.value as TomlInlineTable, target)
          }
      }
    }
    return null as PsiElement?
  }

}

private fun findAlias(valueOwner: TomlKeyValueOwner, target:String): PsiElement?{
  for (entry in valueOwner.entries) {
    val entryKeyText = entry.key.text
    if (keysMatch(entryKeyText, target)) {
      return entry
    }
  }
  return null
}


private fun keysMatch(keyText: String?, reference: String): Boolean {
  keyText ?: return false
  if (keyText.length != reference.length) {
    return false
  }
  for (i in keyText.indices) {
    if(isAfterDelimiter(i, keyText)){
      // first character may be capital after `-_.` symbols in TOML
      // it still makes it equal to low case reference - Gradle implementation detail
      if(keyText[i].normalizeIgnoreCase() != reference[i].normalize())
        return false
    } else if (keyText[i].normalize() != reference[i].normalize()) {
      return false
    }
  }
  return true
}

private fun isAfterDelimiter(index: Int, s:String):Boolean =
  index > 0 && s[index-1].normalize() == '.'

private fun Char.normalizeIgnoreCase(): Char {
  if (this == '-' || this == '_') {
    return '.'
  }
  return this.lowercaseChar()
}

// Gradle converts dashed-keys or dashed_keys into dashed.keys
private fun Char.normalize(): Char {
  if (this == '-' || this == '_') {
    return '.'
  }
  return this
}