// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.ProjectManager

/**
 * Drops the replayed events of [RecentFileEventsModel] after a plugin unload, so that they keep no file of the plugin.
 * The model holds the files of the plugins of this process, so the listener reacts to an unload in this process.
 */
@Suppress("SplitModeApiUsage")
internal class RecentFilesPluginUnloadListener : DynamicPluginListener {
  override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    for (project in ProjectManager.getInstance().openProjects) {
      project.serviceIfCreated<RecentFileEventsModel>()?.resetReplayedEvents()
    }
  }
}
