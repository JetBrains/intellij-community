// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.scaffolding

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

internal fun directoryKind(project: Project, directory: VirtualFile): DirectoryKindInfo? {
  val resourceDirectory = directory.findChild("resources") ?: return null
  val projectFileIndex = ProjectFileIndex.getInstance(project)
  if (projectFileIndex.getSourceRootForFile(resourceDirectory) != resourceDirectory) return null

  val module = projectFileIndex.getModuleForFile(directory) ?: return null
  val rootManager = ModuleRootManager.getInstance(module)

  val contentRoot = rootManager.contentRoots.singleOrNull() ?: return null
  if (directory != contentRoot) return null

  val pluginDescriptorFile = resourceDirectory.findChild("META-INF")?.findChild("plugin.xml")
  val pluginDescriptorPsi = pluginDescriptorFile?.let { getIdeaPluginXml(project, it) }
  if (pluginDescriptorPsi != null) {
    if (rootManager.hasOtherSourceRoots()) {
      return DirectoryKindInfo(DirectoryKind.LEGACY_PLUGIN_WITH_MAIN_MODULE, pluginDescriptorPsi)
    }
    else if (rootManager.getSourceRoots(JavaResourceRootType.RESOURCE).size == 1) {
      return DirectoryKindInfo(DirectoryKind.PLUGIN, pluginDescriptorPsi)
    }
    else {
      return null
    }
  }
  val moduleDescriptorFile = resourceDirectory.findChild(module.name + ".xml")
  val moduleDescriptorPsi = moduleDescriptorFile?.let { getIdeaPluginXml(project, it) }
  if (moduleDescriptorPsi != null) {
    return DirectoryKindInfo(DirectoryKind.MODULE, moduleDescriptorPsi)
  }
  return null
}

private fun ModuleRootManager.hasOtherSourceRoots(): Boolean {
  return getSourceRoots(JavaSourceRootType.SOURCE).isNotEmpty() ||
         getSourceRoots(JavaSourceRootType.TEST_SOURCE).isNotEmpty() ||
         getSourceRoots(JavaResourceRootType.TEST_RESOURCE).isNotEmpty()
}

private fun getIdeaPluginXml(project: Project, file: VirtualFile): XmlFile? {
  val descriptorPsiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
  return descriptorPsiFile.takeIf { DescriptorUtil.isPluginXml(it) }
}

internal data class DirectoryKindInfo(
  val kind: DirectoryKind,
  val descriptorFile: XmlFile,
)

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
