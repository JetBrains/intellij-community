// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.ide

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import org.jetbrains.idea.devkit.util.DescriptorUtil

private class PluginDescriptorEditorTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (PluginManagerCore.PLUGIN_XML != file.name || DumbService.isDumb(project)) {
      return null
    }

    val pluginId = ReadAction.compute<String?, Throwable> {
      val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@compute null

      DescriptorUtil.getIdeaPluginFileElement(xmlFile) ?: return@compute null

      @Suppress("HardCodedStringLiteral")
      PluginIdDependenciesIndex.getPluginId(project, file) ?: "<unknown>"
    } ?: return null

    @Suppress("HardCodedStringLiteral")
    return "${PluginManagerCore.PLUGIN_XML} (${pluginId})"
  }
}