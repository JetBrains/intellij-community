// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.facet.FacetFromExternalSourcesStorage
import com.intellij.facet.FacetManager
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics
import com.intellij.workspace.legacyBridge.externalSystem.ExternalSystemModulePropertyManagerForWorkspaceModel
import com.intellij.workspace.legacyBridge.facet.FacetManagerViaWorkspaceModel
import org.picocontainer.MutablePicoContainer
import java.io.File

internal class LegacyBridgeModuleImpl(
  override var moduleEntityId: ModuleId,
  name: String,
  project: Project,
  filePath: String?,
  override var entityStore: TypedEntityStore,
  override var diff: TypedEntityStorageDiffBuilder?
) : ModuleImpl(name, project, filePath), LegacyBridgeModule {
  private val directoryPath: String? = filePath?.let { File(it).parent }

  init {
    // default project doesn't have modules
    if (!project.isDefault) {
      val busConnection = project.messageBus.connect(this)

      WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, object : WorkspaceModelChangeListener {
        override fun beforeChanged(event: EntityStoreChanged) {
          event.getChanges(ModuleEntity::class.java).filterIsInstance<EntityChange.Removed<ModuleEntity>>()
            .forEach{ if (it.entity.persistentId() == moduleEntityId) entityStore = EntityStoreOnStorage(entityStore.current) }
        }
      })
    }
  }

  override fun rename(newName: String, notifyStorage: Boolean) {
    moduleEntityId = moduleEntityId.copy(name = newName)
    super<ModuleImpl>.rename(newName, notifyStorage)
  }

  override fun registerComponents(plugins: List<DescriptorToLoad>, listenerCallbacks: List<Runnable>?) {
    super.registerComponents(plugins, null)

    val pluginDescriptor = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)
                           ?: error("Could not find plugin by id: ${PluginManagerCore.CORE_ID}")

    registerComponent(ModuleRootManager::class.java, LegacyBridgeModuleRootComponent::class.java, pluginDescriptor, true)
    registerComponent(FacetManager::class.java, FacetManagerViaWorkspaceModel::class.java, pluginDescriptor, true)
    (picoContainer as MutablePicoContainer).unregisterComponent(DeprecatedModuleOptionManager::class.java)

    registerService(LegacyBridgeFilePointerProvider::class.java, LegacyBridgeFilePointerProviderImpl::class.java, pluginDescriptor, false)
    registerService(IComponentStore::class.java, LegacyBridgeModuleStoreImpl::class.java, pluginDescriptor, true)
    registerService(ExternalSystemModulePropertyManager::class.java, ExternalSystemModulePropertyManagerForWorkspaceModel::class.java, pluginDescriptor, true)
    (picoContainer as MutablePicoContainer).unregisterComponent(FacetFromExternalSourcesStorage::class.java.name)
  }

  override fun getModuleFile(): VirtualFile? {
    if (directoryPath == null) return null
    return LocalFileSystem.getInstance().findFileByIoFile(File(moduleFilePath))
  }

  override fun getOptionValue(key: String): String? {
    val moduleEntity = entityStore.current.resolve(moduleEntityId)
    if (key == Module.ELEMENT_TYPE) {
      return moduleEntity?.type
    }
    return moduleEntity?.customImlData?.customModuleOptions?.get(key)
  }

  override fun setOption(key: String, value: String?) {
    fun updateOptionInEntity(diff: TypedEntityStorageDiffBuilder, entity: ModuleEntity) {
      if (key == Module.ELEMENT_TYPE) {
        diff.modifyEntity(ModifiableModuleEntity::class.java, entity, { type = value })
      }
      else {
        val customImlData = entity.customImlData
        if (customImlData == null) {
          if (value != null) {
            diff.addModuleCustomImlDataEntity(null, mapOf(key to value), entity, entity.entitySource)
          }
        }
        else {
          diff.modifyEntity(ModifiableModuleCustomImlDataEntity::class.java, customImlData) {
            if (value != null) {
              customModuleOptions[key] = value
            }
            else {
              customModuleOptions.remove(key)
            }
          }
        }
      }
    }

    val diff = diff
    if (diff != null) {
      val entity = entityStore.current.resolve(moduleEntityId)
      if (entity != null) {
        updateOptionInEntity(diff, entity)
      }
    }
    else {
      WriteAction.runAndWait<RuntimeException> {
        WorkspaceModel.getInstance(project).updateProjectModel { builder ->
          val entity = builder.resolve(moduleEntityId)
          if (entity != null) {
            updateOptionInEntity(builder, entity)
          }
        }
      }
    }
    return

  }

  override fun getOptionsModificationCount(): Long = 0

  override fun getModuleFilePath(): String = directoryPath?.let { "$it/$name${ModuleFileType.DOT_DEFAULT_EXTENSION}" } ?: ""
}