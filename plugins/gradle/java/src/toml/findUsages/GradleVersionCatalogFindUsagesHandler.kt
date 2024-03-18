// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml.findUsages

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.config.GradleBuildscriptSearchScope
import org.toml.lang.psi.TomlKeySegment

class GradleVersionCatalogFindUsagesHandler(private val tomlElement: TomlKeySegment) : FindUsagesHandler(tomlElement) {

  override fun getFindUsagesOptions(dataContext: DataContext?): FindUsagesOptions {
    val superOptions = super.getFindUsagesOptions(dataContext)
    superOptions.searchScope = superOptions.searchScope.union(VersionCatalogSearchScope(tomlElement))
    return superOptions
  }

  private class VersionCatalogSearchScope(context: PsiElement) : GlobalSearchScope(context.project) {
    private val buildscriptScope = GradleBuildscriptSearchScope(context.project)
    private val tomlScope = fileScope(context.containingFile)

    override fun contains(file: VirtualFile): Boolean = buildscriptScope.contains(file) || tomlScope.contains(file)

    override fun isSearchInModuleContent(aModule: Module): Boolean = buildscriptScope.isSearchInModuleContent(aModule)

    override fun isSearchInLibraries(): Boolean = buildscriptScope.isSearchInLibraries

    override fun getDisplayName(): String {
      return GradleInspectionBundle.message("gradle.version.catalog.search.scope")
    }
  }

}
