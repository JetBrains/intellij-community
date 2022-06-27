// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.storage.VersionedStorageChange

class GroovyMacroModuleListener : ModuleListener, ModuleRootListener, WorkspaceModelChangeListener {

  override fun moduleAdded(project: Project, module: Module) = project.service<GroovyMacroRegistryService>().refreshModule(module)

  override fun moduleRemoved(project: Project, module: Module) = project.service<GroovyMacroRegistryService>().refreshModule(module)

  override fun rootsChanged(event: ModuleRootEvent) {
    refreshAllModules(event.project)
  }

  override fun beforeChanged(event: VersionedStorageChange) {
    val project = getProject(event) ?: return
    refreshAllModules(project)
  }

  override fun changed(event: VersionedStorageChange) {
    val project = getProject(event) ?: return
    refreshAllModules(project)
  }

  private fun getProject(event: VersionedStorageChange) : Project? {
    var project : Project? = null
    event.storageAfter.moduleMap.forEach { _, bridge -> project = bridge.project}
    return project
  }

  private fun refreshAllModules(project: Project) {
    val service = project.service<GroovyMacroRegistryService>()
    for (module in ModuleManager.getInstance(project).modules) {
      service.refreshModule(module)
    }
  }
}