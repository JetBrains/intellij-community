// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.ide

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex

internal class PluginDescriptorEditorTabTitleProvider : EditorTabTitleProvider {

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (PluginManagerCore.PLUGIN_XML != file.name) return null

    val pluginId = ReadAction.compute<String?, Throwable> {
      if (DumbService.isDumb(project)) {
        return@compute null
      }

      PluginIdDependenciesIndex.getPluginId(project, file)
    } ?: return null

    return "${PluginManagerCore.PLUGIN_XML} (${pluginId})"
  }

  override suspend fun getEditorTabTitleAsync(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    if (PluginManagerCore.PLUGIN_XML != file.name) return null

    val pluginId = readAction {
      if (DumbService.isDumb(project)) {
        return@readAction null
      }

      PluginIdDependenciesIndex.getPluginId(project, file)
    } ?: return null

    return "${PluginManagerCore.PLUGIN_XML} (${pluginId})"
  }
}