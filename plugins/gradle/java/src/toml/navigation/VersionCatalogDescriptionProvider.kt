// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml.navigation

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.isInVersionCatalog
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

class VersionCatalogDescriptionProvider : ElementDescriptionProvider {
  override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
    if (element !is TomlKeySegment) return null
    if (!isInVersionCatalog(element)) return null
    if (!isVersionCatalogAlias(element)) return null

    return when(location) {
      is UsageViewShortNameLocation -> element.name
      is UsageViewTypeLocation -> GradleInspectionBundle.message("element.description.version.catalog.alias")
      else -> null
    }
  }
}

private val catalogSections = setOf("libraries", "bundles", "plugins", "versions")

private fun isVersionCatalogAlias(psiElement: TomlKeySegment): Boolean {
  val keyValue = psiElement.parentOfType<TomlKeyValue>() ?: return false
  val table = keyValue.parent.asSafely<TomlTable>() ?: return false
  val tableName = table.header.key?.text ?: return false
  return catalogSections.contains(tableName)
}