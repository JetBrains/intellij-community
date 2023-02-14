// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.static

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogHandler

class GradleDslVersionCatalogHandler : GradleVersionCatalogHandler {
  override fun getExternallyHandledExtension(project: Project): Set<String> {
    // todo
    return getVersionCatalogFiles(project).takeIf { it.isNotEmpty() }?.let { setOf("libs") } ?: emptySet()
  }

  override fun getVersionCatalogFiles(project: Project): Map<String, VirtualFile> {
    return ProjectBuildModel.get(project).context.rootProjectFile?.versionCatalogFiles?.associate { it.catalogName to it.file } ?: emptyMap()
  }

  override fun getAccessorClass(context: PsiElement, catalogName: String): PsiClass? {
    val project = context.project
    val scope = context.resolveScope
    val versionCatalogModel = ProjectBuildModel.get(project).versionCatalogModel ?: return null
    return SyntheticVersionCatalogAccessor(project, scope, versionCatalogModel, catalogName)
  }
}