// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.ide

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import org.jetbrains.idea.devkit.util.DescriptorUtil

class PluginDescriptorEditorTabTitleProvider : EditorTabTitleProvider {

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (PluginManagerCore.PLUGIN_XML != file.name) return null

    val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null

    DescriptorUtil.getIdeaPluginFileElement(xmlFile) ?: return null

    val pluginId = PluginIdDependenciesIndex.getPluginId(project, file)

    @Suppress("HardCodedStringLiteral")
    return "${PluginManagerCore.PLUGIN_XML} (${pluginId ?: "<unknown>"})"
  }
}