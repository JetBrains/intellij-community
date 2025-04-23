// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.scaffolding

import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import javax.swing.Icon

internal class PluginDirectoryIconProvider : IconProvider() {

  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    if (!Registry.`is`("devkit.plugin.directory.icons")) {
      return null
    }
    if (element is PsiDirectory) {
      val project = element.project
      if (IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
        if (isWellFormedPluginDirectory(project, element.virtualFile)) {
          return AllIcons.Nodes.Plugin
        }
      }
    }
    return null
  }
}

/**
 * Returns `true` if all the following is true:
 * - `dir` is a single content root of a module
 * - `dir/resources` is a single resource root of that module
 * - `dir/resources/META-INF/plugin.xml` is a plugin descriptor
 * - the module does not have source roots
 */
private fun isWellFormedPluginDirectory(project: Project, directory: VirtualFile): Boolean {
  val resourceDirectory = directory.findChild("resources") ?: return false
  val pluginFile = resourceDirectory.findChild("META-INF")?.findChild("plugin.xml") ?: return false
  val module = ProjectFileIndex.getInstance(project).getModuleForFile(directory) ?: return false
  val rootManager = ModuleRootManager.getInstance(module)
  val contentRoot = rootManager.contentRoots.singleOrNull() ?: return false
  if (directory != contentRoot) return false
  val resourceRoot = rootManager.getSourceRoots(JavaResourceRootType.RESOURCE).singleOrNull() ?: return false
  if (resourceDirectory != resourceRoot) return false
  if (rootManager.getSourceRoots(JavaSourceRootType.SOURCE).isNotEmpty()) return false
  if (rootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE).isNotEmpty()) return false
  val pluginPsiFile = PsiManager.getInstance(project).findFile(pluginFile) ?: return false
  return DescriptorUtil.isPluginXml(pluginPsiFile)
}
