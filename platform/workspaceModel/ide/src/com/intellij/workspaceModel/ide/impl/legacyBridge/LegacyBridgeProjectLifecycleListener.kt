// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ExternalModuleListStorage
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceContainerCustomizer
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.ModifiableModelCommitterService
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.util.registry.Registry
import com.intellij.project.ProjectStoreOwner
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelInitialTestContent
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import com.intellij.workspaceModel.ide.impl.legacyBridge.externalSystem.ExternalStorageConfigurationManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetEntityChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FilePointerProvider
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FilePointerProviderImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.RootsChangeWatcher
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModifiableModelCommitterServiceBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectStoreBridge
import org.jetbrains.annotations.ApiStatus
import org.picocontainer.MutablePicoContainer

@ApiStatus.Internal
class LegacyBridgeProjectLifecycleListener : ProjectServiceContainerCustomizer {
  companion object {
    const val ENABLED_REGISTRY_KEY = "ide.new.project.model"
    const val ENABLED_CACHE_KEY = "ide.new.project.model.cache"

    private val LOG = logger<LegacyBridgeProjectLifecycleListener>()

    fun enabled(project: Project) = ModuleManager.getInstance(project) is ModuleManagerComponentBridge
    val cacheEnabled = Registry.`is`(ENABLED_CACHE_KEY)
  }

  override fun serviceRegistered(project: Project) {
    val enabled = Registry.`is`(ENABLED_REGISTRY_KEY) || WorkspaceModelInitialTestContent.peek() != null
    if (!enabled) {
      LOG.info("Using legacy project model to open project")
      return
    }

    LOG.info("Using workspace model to open project")

    val pluginDescriptor = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)
                           ?: error("Could not find plugin by id: ${PluginManagerCore.CORE_ID}")

    val container = project as ComponentManagerImpl

    (project as ProjectStoreOwner).componentStore = ProjectStoreBridge(project)
    container.registerComponent(JpsProjectModelSynchronizer::class.java, JpsProjectModelSynchronizer::class.java, pluginDescriptor, false)
    container.registerComponent(RootsChangeWatcher::class.java, RootsChangeWatcher::class.java, pluginDescriptor, false)
    container.registerComponent(ModuleManager::class.java, ModuleManagerComponentBridge::class.java, pluginDescriptor, true)
    container.registerComponent(ProjectRootManager::class.java, ProjectRootManagerBridge::class.java, pluginDescriptor, true)
    (container.picoContainer as MutablePicoContainer).unregisterComponent(ExternalModuleListStorage::class.java)

    container.registerService(FilePointerProvider::class.java, FilePointerProviderImpl::class.java, pluginDescriptor, false)
    container.registerService(WorkspaceModel::class.java, WorkspaceModelImpl::class.java, pluginDescriptor, false)
    container.registerService(ProjectLibraryTable::class.java, ProjectLibraryTableBridgeImpl::class.java, pluginDescriptor, true)
    container.registerService(ExternalStorageConfigurationManager::class.java, ExternalStorageConfigurationManagerBridge::class.java, pluginDescriptor, true)
    container.registerService(ModifiableModelCommitterService::class.java, ModifiableModelCommitterServiceBridge::class.java, pluginDescriptor, true)
    container.registerService(WorkspaceModelTopics::class.java, WorkspaceModelTopics::class.java, pluginDescriptor, false)
    container.registerService(FacetEntityChangeListener::class.java, FacetEntityChangeListener::class.java, pluginDescriptor, false)
  }
}