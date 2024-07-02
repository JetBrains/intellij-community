// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.ide

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex

internal class PluginDescriptorEditorTabTitleProvider : EditorTabTitleProvider {

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (PluginManagerCore.PLUGIN_XML != file.name ||
        DumbService.isDumb(project)) {
      return null
    }

    val pluginId = ReadAction.compute<String?, Throwable> {
      PluginIdDependenciesIndex.getPluginId(project, file)
    } ?: return null

    return "${PluginManagerCore.PLUGIN_XML} (${pluginId})"
  }
}