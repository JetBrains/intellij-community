// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle.toml

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import org.jetbrains.idea.devkit.gradle.IntelliJPlatformVersionCatalogUpdater
import org.jetbrains.annotations.ApiStatus
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import org.toml.lang.psi.ext.name

@ApiStatus.Internal
class TomlIntelliJPlatformVersionCatalogUpdater : IntelliJPlatformVersionCatalogUpdater {
  override fun findReplacements(file: PsiFile, currentVersion: String, latestVersion: String): Map<PsiElement, String> {
    if (file !is TomlFile) return emptyMap()

    return buildMap {
      val versionReferences = mutableSetOf<String>()
      for (plugin in file.table("plugins")?.entries.orEmpty()) {
        when (val declaration = plugin.value) {
          is TomlInlineTable -> {
            if (!IntelliJPlatformVersionCatalogUpdater.isPluginId(declaration.literal("id")?.plainString())) continue
            declaration.literal("version")?.takeIf { it.plainString() == currentVersion }?.let { put(it, latestVersion) }
            declaration.literal("version.ref")?.plainString()?.let(versionReferences::add)
          }
          is TomlLiteral -> {
            val coordinates = declaration.plainString()?.split(':')?.takeIf { it.size == 2 } ?: continue
            if (!IntelliJPlatformVersionCatalogUpdater.isPluginId(coordinates[0]) || coordinates[1] != currentVersion) continue
            put(declaration, coordinates[0] + ":" + latestVersion)
          }
        }
      }

      for (version in file.table("versions")?.entries.orEmpty()) {
        val value = version.value as? TomlLiteral ?: continue
        if (version.key.name in versionReferences && value.plainString() == currentVersion) put(value, latestVersion)
      }
    }
  }
}

private fun TomlFile.table(name: String): TomlTable? = childrenOfType<TomlTable>().firstOrNull { it.header.key?.name == name }

private fun TomlInlineTable.literal(name: String): TomlLiteral? {
  val direct = entries.firstOrNull { it.key.text == name }?.value
  if (direct is TomlLiteral) return direct
  if (name == "version.ref") {
    val versionEntry = entries.firstOrNull { it.key.text == "version" }?.value as? TomlInlineTable
    return versionEntry?.entries?.firstOrNull { it.key.text == "ref" }?.value as? TomlLiteral
  }
  return null
}

private fun TomlLiteral.plainString(): String? = (kind as? TomlLiteralKind.String)?.value
