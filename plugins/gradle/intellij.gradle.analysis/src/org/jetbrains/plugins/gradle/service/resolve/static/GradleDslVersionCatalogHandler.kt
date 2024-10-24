// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.static

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogHandler

class GradleDslVersionCatalogHandler : GradleVersionCatalogHandler {
  @Deprecated("Doesn't work for included builds of a composite build", replaceWith = ReplaceWith("getVersionCatalogFiles(module)"))
  override fun getExternallyHandledExtension(project: Project): Set<String> {
    // todo
    return getVersionCatalogFiles(project).takeIf { it.isNotEmpty() }?.let { setOf("libs") } ?: emptySet()
  }

  @Deprecated("Doesn't work for included builds of a composite build", replaceWith = ReplaceWith("getVersionCatalogFiles(module)"))
  override fun getVersionCatalogFiles(project: Project): Map<String, VirtualFile> {
    return ProjectBuildModel.get(project).context.versionCatalogFiles.associate { it.catalogName to it.file } ?: emptyMap()
  }

  override fun getVersionCatalogFiles(module: Module): Map<String, VirtualFile> {
    val buildModel = getBuildModel(module) ?: return emptyMap()
    return buildModel.context.versionCatalogFiles.associate { it.catalogName to it.file }
  }

  override fun getAccessorClass(context: PsiElement, catalogName: String): PsiClass? {
    val project = context.project
    val scope = context.resolveScope
    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return null
    val buildModel = getBuildModel(module) ?: return null
    val versionCatalogModel = buildModel.versionCatalogsModel
    return SyntheticVersionCatalogAccessor(project, scope, versionCatalogModel, catalogName)
  }

  private fun getBuildModel(module: Module): ProjectBuildModel? {
    val buildPath = ExternalSystemModulePropertyManager.getInstance(module)
      .getLinkedProjectPath() ?: return null
    return ProjectBuildModel.getForCompositeBuild(module.project, buildPath)
  }
}
