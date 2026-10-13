// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.ProjectManager

/**
 * Removes the files of an unloaded plugin from [FrontendRecentFilesModel] after a plugin unload.
 */
internal class FrontendRecentFilesPluginUnloadListener : DynamicPluginListener {
  override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    for (project in ProjectManager.getInstance().openProjects) {
      project.serviceIfCreated<FrontendRecentFilesModel>()?.removeFilesOfUnloadingPlugins()
    }
  }
}
