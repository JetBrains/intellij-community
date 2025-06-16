// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.scaffolding

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

internal fun directoryKind(project: Project, directory: VirtualFile): DirectoryKind? {
  val resourceDirectory = directory.findChild("resources") ?: return null
  val projectFileIndex = ProjectFileIndex.getInstance(project)
  if (projectFileIndex.getSourceRootForFile(resourceDirectory) != resourceDirectory) return null

  val module = projectFileIndex.getModuleForFile(directory) ?: return null
  val rootManager = ModuleRootManager.getInstance(module)

  val contentRoot = rootManager.contentRoots.singleOrNull() ?: return null
  if (directory != contentRoot) return null

  val pluginDescriptorFile = resourceDirectory.findChild("META-INF")?.findChild("plugin.xml")
  if (pluginDescriptorFile != null && isIdeaPluginXml(project, pluginDescriptorFile)) {
    if (rootManager.hasOtherSourceRoots()) {
      return DirectoryKind.LEGACY_PLUGIN_WITH_MAIN_MODULE
    }
    else if (rootManager.getSourceRoots(JavaResourceRootType.RESOURCE).size == 1) {
      return DirectoryKind.PLUGIN
    }
    else {
      return null
    }
  }
  val moduleDescriptorFile = resourceDirectory.findChild(module.name + ".xml")
  if (moduleDescriptorFile != null && isIdeaPluginXml(project, moduleDescriptorFile)) {
    return DirectoryKind.MODULE
  }
  return null
}

private fun ModuleRootManager.hasOtherSourceRoots(): Boolean {
  return getSourceRoots(JavaSourceRootType.SOURCE).isNotEmpty() ||
         getSourceRoots(JavaSourceRootType.TEST_SOURCE).isNotEmpty() ||
         getSourceRoots(JavaResourceRootType.TEST_RESOURCE).isNotEmpty()
}

private fun isIdeaPluginXml(project: Project, file: VirtualFile): Boolean {
  val descriptorPsiFile = PsiManager.getInstance(project).findFile(file) ?: return false
  return DescriptorUtil.isPluginXml(descriptorPsiFile)
}

internal enum class DirectoryKind {

  /**
   * - `dir` is a single content root of a module
   * - `dir/resources` is a single resource root of that module
   * - `dir/resources/META-INF/plugin.xml` is a plugin descriptor
   * - the module does not have source roots
   */
  PLUGIN,

  /**
   * - `dir` is a single content root of a module
   * - `dir/resources` is a resource root of that module
   * - `dir/resources/META-INF/plugin.xml` is a plugin descriptor
   * - the module has source roots
   */
  LEGACY_PLUGIN_WITH_MAIN_MODULE,

  /**
   * - `dir` is not a [PLUGIN]
   * - `dir/resources` is a resource root of that module
   * - `dir/resources/moduleName.xml` is a plugin descriptor
   */
  MODULE,
}
