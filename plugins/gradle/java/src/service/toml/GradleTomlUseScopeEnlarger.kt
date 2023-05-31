// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.toml

import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import org.jetbrains.plugins.gradle.config.GradleBuildscriptSearchScope
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles
import org.toml.lang.psi.TomlElement

class GradleTomlUseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    if (element !is TomlElement) return null
    val containingFile = element.containingFile?.virtualFile ?: return null
    val versionCatalogFiles = getVersionCatalogFiles(element.project).values
    if (containingFile !in versionCatalogFiles) {
      return null
    }
    return GradleBuildscriptSearchScope(element.project)
  }
}