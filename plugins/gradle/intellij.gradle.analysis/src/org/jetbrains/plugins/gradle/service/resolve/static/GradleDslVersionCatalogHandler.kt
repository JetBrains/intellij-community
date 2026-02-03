// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles as getVersionCatalogFilesCommon

internal class GradleDslVersionCatalogHandler : GradleVersionCatalogHandler {
  @Deprecated("Doesn't work for included builds of a composite build", replaceWith = ReplaceWith("getVersionCatalogFiles(module)"))
  override fun getVersionCatalogFiles(project: Project): Map<String, VirtualFile> {
    return ProjectBuildModel.get(project).context.versionCatalogFiles.associate { it.catalogName to it.file }
  }

  override fun getAccessorClass(context: PsiElement, catalogName: String): PsiClass? {
    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return null
    val versionCatalogsModel = getBuildModel(module)?.versionCatalogsModel ?: return null

    var catalogModel = versionCatalogsModel.getVersionCatalogModel(catalogName)
    if (catalogModel == null) {
      // Call ALL GradleVersionCatalogHandler's in case one of them provides a catalog unavailable to this one.
      val catalogVirtualFile = getVersionCatalogFilesCommon(module)[catalogName] ?: return null
      catalogModel = versionCatalogsModel.getVersionCatalogModel(catalogVirtualFile, catalogName)
    }
    return SyntheticVersionCatalogAccessor.create(context.project, context.resolveScope, catalogModel, catalogName)
  }

  override fun getAccessorsForAllCatalogs(context: PsiElement): Map<String, PsiClass> {
    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return emptyMap()
    val versionCatalogsModel = getBuildModel(module)?.versionCatalogsModel ?: return emptyMap()

    // Call ALL GradleVersionCatalogHandler's in case others could provide catalogs unavailable to this handler.
    val catalogNameToFile = getVersionCatalogFilesCommon(module)
    return catalogNameToFile.mapNotNull { (catalogName, virtualFile) ->
      val catalogModel = versionCatalogsModel.getVersionCatalogModel(virtualFile, catalogName)
      val accessor = SyntheticVersionCatalogAccessor.create(context.project, context.resolveScope, catalogModel, catalogName)
                     ?: return@mapNotNull null
      catalogName to accessor
    }.toMap()
  }

  private fun getBuildModel(module: Module): ProjectBuildModel? {
    val buildPath = ExternalSystemModulePropertyManager.getInstance(module)
      .getLinkedProjectPath() ?: return null
    return ProjectBuildModel.getForCompositeBuild(module.project, buildPath)
  }
}
