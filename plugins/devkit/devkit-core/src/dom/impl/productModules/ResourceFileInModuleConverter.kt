// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl.productModules

import com.intellij.java.workspace.files.findResourceFileByRelativePath
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiFile
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.ResolvingConverter
import org.jetbrains.idea.devkit.DevKitBundle

internal abstract class ResourceFileInModuleConverter : ResolvingConverter<PsiFile>() {
  override fun fromString(s: String?, context: ConvertContext): PsiFile? {
    val module = resolveModule(s, context) ?: return null
    return findResourceFile(module, context.project)
  }
  
  private fun resolveModule(s: String?, context: ConvertContext): ModuleEntity? {
    val moduleName = s ?: return null
    val moduleId = ModuleId(moduleName)
    return context.project.workspaceModel.currentSnapshot.resolve(moduleId)
  }

  override fun getErrorMessage(s: String?, context: ConvertContext): String? {
    val module = resolveModule(s, context)
    if (module == null) {
      return DevKitBundle.message("error.message.cannot.find.module", s)
    } 
    return DevKitBundle.message("error.message.cannot.find.resource.file", getResourceFilePath(module.name), module.name)
  }

  private fun findResourceFile(module: ModuleEntity, project: Project): PsiFile? {
    val file = module.findResourceFileByRelativePath(getResourceFilePath(module.name))
    return file?.findPsiFile(project)
  }

  protected abstract fun getResourceFilePath(moduleName: String): String

  override fun toString(t: PsiFile?, context: ConvertContext): String? {
    val psiFile = t ?: return null
    return ModuleUtil.findModuleForPsiElement(psiFile)?.name
  }

  override fun getVariants(context: ConvertContext): Collection<PsiFile> {
    return context.project.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).mapNotNullTo(ArrayList()) {
      findResourceFile(it, context.project)
    }
  }
}

internal class ProductModulesXmlFileConverter : ResourceFileInModuleConverter() {
  override fun getResourceFilePath(moduleName: String): String = "META-INF/$moduleName/product-modules.xml"
}

internal class PluginXmlFileConverter : ResourceFileInModuleConverter() {
  override fun getResourceFilePath(moduleName: String): String = "META-INF/plugin.xml"
}