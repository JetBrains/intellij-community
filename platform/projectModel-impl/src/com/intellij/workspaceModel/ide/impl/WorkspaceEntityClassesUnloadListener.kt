// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.storage.impl.forgetWorkspaceEntityClasses

/**
 * Drops the entity classes of unloaded modules from the class id registry of the workspace storage.
 * It listens to [pluginsUnloaded], not to the per-plugin events: a content module of a plugin that stays loaded
 * publishes no [DynamicPluginListener.pluginUnloaded], and only the class loader state names its unload.
 * A class that a current snapshot of an open project or of a global workspace model still holds stays registered.
 */
internal class WorkspaceEntityClassesUnloadListener : DynamicPluginListener {
  override fun pluginsUnloaded() {
    val liveStorages = ProjectManager.getInstance().openProjects.flatMap {
      val model = WorkspaceModel.getInstance(it) as WorkspaceModelInternal
      listOf(model.currentSnapshot, model.currentSnapshotOfUnloadedEntities)
    } + GlobalWorkspaceModel.getInstancesBlocking().map { it.currentSnapshot }
    forgetWorkspaceEntityClasses(liveStorages) { isOfUnloadingModule(it) }
  }
}

private fun isOfUnloadingModule(aClass: Class<*>): Boolean {
  return (aClass.classLoader as? PluginAwareClassLoader)?.state == PluginAwareClassLoader.UNLOAD_IN_PROGRESS
}
