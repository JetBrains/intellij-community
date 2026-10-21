// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Removes the files of an unloaded plugin from [RecentFileEventsModel] and [RecentFilesModel] after a plugin unload.
 * The models hold the files of the plugins of this process, so the listener reacts to an unload in this process.
 */
@Suppress("SplitModeApiUsage")
internal class RecentFilesPluginUnloadListener : DynamicPluginListener {
  override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    for (project in ProjectManager.getInstance().openProjects) {
      forgetFilesOfUnloadingPlugins(project)
    }
  }
}

/**
 * Drops the replayed events of [RecentFileEventsModel], and removes the files of unloading plugins from [RecentFilesModel].
 */
internal fun forgetFilesOfUnloadingPlugins(project: Project) {
  project.serviceIfCreated<RecentFileEventsModel>()?.resetReplayedEvents()
  project.serviceIfCreated<RecentFilesModel>()?.removeFilesOfUnloadingPlugins()
}

/**
 * Tells whether the class of [file] or of its file system comes from a plugin class loader that is not ACTIVE.
 */
@ApiStatus.Internal
fun isFileOfUnloadingPlugin(file: VirtualFile): Boolean {
  return isUnloadingClass(file.javaClass) || isUnloadingClass(file.fileSystem.javaClass)
}

private fun isUnloadingClass(aClass: Class<*>): Boolean {
  val classLoader = aClass.classLoader as? PluginAwareClassLoader ?: return false
  return classLoader.state != PluginAwareClassLoader.ACTIVE
}
