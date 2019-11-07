// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectServiceContainerCustomizer
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.ModifiableModelCommitterService
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.util.registry.Registry
import com.intellij.serviceContainer.PlatformComponentManagerImpl
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.ide.WorkspaceModelImpl
import com.intellij.workspace.jps.JpsProjectModelSynchronizer
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeProjectLibraryTableImpl
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeRootsWatcher

internal class LegacyBridgeProjectLifecycleListener : ProjectServiceContainerCustomizer {
  companion object {
    const val ENABLED_REGISTRY_KEY = "ide.new.project.model"

    private val LOG = logger<LegacyBridgeProjectLifecycleListener>()

    fun enabled(project: Project) = ModuleManager.getInstance(project) is LegacyBridgeModuleManagerComponent
  }

  override fun serviceContainerInitialized(project: Project) {
    val enabled = Registry.`is`(ENABLED_REGISTRY_KEY)
    if (!enabled) {
      LOG.info("Using legacy project model to open project")
      return
    }

    // TODO new -> XXX
    LOG.info("Using new project model to open project")

    val pluginId = PluginManager.getPluginOrPlatformByClassName(javaClass.name)
                   ?: error("Could not find pluginId for class ${javaClass.name}")
    val pluginDescriptor = PluginManagerCore.getPlugin(pluginId)
                           ?: error("Could not find plugin by id: $pluginId")

    val container = project as PlatformComponentManagerImpl

    (project as ProjectImpl).setProjectStoreFactory(LegacyBridgeProjectStoreFactory())
    container.registerComponent(JpsProjectModelSynchronizer::class.java, JpsProjectModelSynchronizer::class.java, pluginDescriptor, false)
    container.registerComponent(LegacyBridgeRootsWatcher::class.java, LegacyBridgeRootsWatcher::class.java, pluginDescriptor, false)
    container.registerComponent(ModuleManager::class.java, LegacyBridgeModuleManagerComponent::class.java, pluginDescriptor, true)
    container.registerComponent(ProjectRootManager::class.java, LegacyBridgeProjectRootManager::class.java, pluginDescriptor, true)

    container.registerService(LegacyBridgeFilePointerProvider::class.java, LegacyBridgeFilePointerProviderImpl::class.java, pluginDescriptor, false)
    container.registerService(WorkspaceModel::class.java, WorkspaceModelImpl::class.java, pluginDescriptor, false)
    container.registerService(ProjectLibraryTable::class.java, LegacyBridgeProjectLibraryTableImpl::class.java, pluginDescriptor, true)
    container.registerService(ModifiableModelCommitterService::class.java, LegacyBridgeModifiableModelCommitterService::class.java, pluginDescriptor, true)
  }
}