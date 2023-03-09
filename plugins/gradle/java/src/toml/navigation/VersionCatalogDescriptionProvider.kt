// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml.navigation

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles
import org.jetbrains.plugins.gradle.toml.getVersions
import org.toml.lang.psi.TomlKeySegment

class VersionCatalogDescriptionProvider : ElementDescriptionProvider {
  override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
    if (element !is TomlKeySegment) return null
    if (getVersionCatalogFiles(element.project).values.find { it == element.containingFile?.virtualFile } == null) {
      return null
    }
    val versions = getVersions(element)
    if (element in versions) {
      return GradleInspectionBundle.message("element.description.version.catalog.alias", element.name)
    }
    return null
  }
}