// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.filterModuleLibraryChanges
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.fireModulesAdded
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.getImlFileDirectory
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.getModuleVirtualFileUrl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex

internal class LegacyProjectModelListenersBridge(
  private val project: Project,
  private val moduleModificationTracker: SimpleModificationTracker,                                              
  private val moduleRootListenerBridge: ModuleRootListenerBridge
) : WorkspaceModelChangeListener {

  /**
   * This is a flag indicating that the [beforeChanged] method was called. Due to the fact that we subscribe using the code, this
   *   may lead to IDEA-324532.
   * With this flag we skip the "after" event if the before event wasn't called.
   */
  private var beforeCalled = false

  override fun beforeChanged(event: VersionedStorageChange) {
    LOG.trace { "Get before changed event" }
    beforeCalled = true
    moduleRootListenerBridge.fireBeforeRootsChanged(project, event)
    val moduleMap = event.storageBefore.moduleMap
    for (change in event.getChanges(ModuleEntity::class.java)) {
      if (change is EntityChange.Removed) {
        val module = moduleMap.getDataByEntity(change.entity)
        LOG.debug { "Fire 'beforeModuleRemoved' event for module ${change.entity.name}, module = $module" }
        if (module != null) {
          fireBeforeModuleRemoved(module)
        }
      }
    }
  }

  override fun changed(event: VersionedStorageChange) {
    if (!beforeCalled) return
    beforeCalled = false
    LOG.trace { "Get changed event" }
    val moduleLibraryChanges = event.getChanges(LibraryEntity::class.java).filterModuleLibraryChanges()
    val changes = event.getChanges(ModuleEntity::class.java)
    if (changes.isNotEmpty() || moduleLibraryChanges.isNotEmpty()) {
      LOG.debug("Process changed modules and facets")
      moduleModificationTracker.incModificationCount()
      for (change in moduleLibraryChanges) {
        when (change) {
          is EntityChange.Removed -> processModuleLibraryChange(change, event)
          is EntityChange.Replaced -> processModuleLibraryChange(change, event)
          is EntityChange.Added -> Unit
        }
      }

      val oldModuleNames = mutableMapOf<Module, String>()
      for (change in changes) {
        processModuleChange(change, oldModuleNames, event)
      }

      for (change in moduleLibraryChanges) {
        if (change is EntityChange.Added) processModuleLibraryChange(change, event)
      }
      // After every change processed
      postProcessModules(oldModuleNames)
      moduleModificationTracker.incModificationCount()
    }
    (ModuleDependencyIndex.getInstance(project) as ModuleDependencyIndexImpl).workspaceModelChanged(event)
    LOG.trace { "fire roots changed for moduleRootListenerBridge" }
    moduleRootListenerBridge.fireRootsChanged(project, event)
  }

  private fun postProcessModules(oldModuleNames: MutableMap<Module, String>) {
    if (oldModuleNames.isNotEmpty()) {
      project.messageBus
        .syncPublisher(ModuleListener.TOPIC)
        .modulesRenamed(project, oldModuleNames.keys.toList()) { module -> oldModuleNames[module] }
    }
  }

  private fun processModuleChange(change: EntityChange<ModuleEntity>, oldModuleNames: MutableMap<Module, String>,
                                  event: VersionedStorageChange) {
    when (change) {
      is EntityChange.Removed -> {
        // it's a possible case then idToModule doesn't contain an element e.g. if unloaded module was removed
        val module = change.entity.findModule(event.storageBefore)
        if (module != null) {
          fireEventAndDisposeModule(module)
        }
      }

      is EntityChange.Added -> {
        removeUnloadedModuleWithId(change.entity.symbolicId)
        val alreadyCreatedModule = change.entity.findModule(event.storageAfter)
        val module = if (alreadyCreatedModule != null) {
          alreadyCreatedModule.entityStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).entityStorage
          alreadyCreatedModule.diff = null
          alreadyCreatedModule
        }
        else {
          error("Module bridge should already be created")
        }

        if (project.isOpen) {
          fireModuleAddedInWriteAction(module)
        }
      }

      is EntityChange.Replaced -> {
        val oldId = change.oldEntity.symbolicId
        val newId = change.newEntity.symbolicId

        if (oldId != newId) {
          removeUnloadedModuleWithId(newId)
          val module = change.oldEntity.findModule(event.storageBefore)
          if (module != null) {
            module.rename(newId.name, getModuleVirtualFileUrl(change.newEntity), true)
            oldModuleNames[module] = oldId.name
          }
        }
        else if (getImlFileDirectory(change.oldEntity) != getImlFileDirectory(change.newEntity)) {
          val module = change.newEntity.findModule(event.storageBefore)
          val imlFilePath = getModuleVirtualFileUrl(change.newEntity)
          if (module != null && imlFilePath != null) {
            module.onImlFileMoved(imlFilePath)
          }
        }
      }
    }
  }

  private fun removeUnloadedModuleWithId(moduleId: ModuleId) {
    val unloadedEntity = WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities.resolve(moduleId)
    if (unloadedEntity != null) {
      WorkspaceModel.getInstance(project).updateUnloadedEntities(
        "Remove module '${moduleId.name}' from unloaded storage because a module with same name is added") {
        it.removeEntity(unloadedEntity)
      }
    }
  }

  private fun processModuleLibraryChange(change: EntityChange<LibraryEntity>, event: VersionedStorageChange) {
    when (change) {
      is EntityChange.Removed -> {
        val library = event.storageBefore.libraryMap.getDataByEntity(change.entity)
        if (library != null) {
          Disposer.dispose(library)
        }
      }
      is EntityChange.Replaced -> {
        val idBefore = change.oldEntity.symbolicId
        val idAfter = change.newEntity.symbolicId

        val newLibrary = event.storageAfter.libraryMap.getDataByEntity(change.newEntity) as LibraryBridgeImpl?
        if (newLibrary != null) {
          newLibrary.clearTargetBuilder()
          if (idBefore != idAfter) {
            newLibrary.entityId = idAfter
          }
        }
      }
      is EntityChange.Added -> {
        val library = event.storageAfter.libraryMap.getDataByEntity(change.entity)
        if (library != null) {
          (library as LibraryBridgeImpl).entityStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).entityStorage
          library.clearTargetBuilder()
        }
      }
    }
  }

  private fun fireModuleAddedInWriteAction(module: ModuleEx) {
    ApplicationManager.getApplication().runWriteAction {
      if (!module.isLoaded) {
        @Suppress("removal", "DEPRECATION")
        val oldComponents = mutableListOf<com.intellij.openapi.module.ModuleComponent>()
        module.moduleAdded(oldComponents)
        for (oldComponent in oldComponents) {
          @Suppress("DEPRECATION", "removal")
          oldComponent.moduleAdded()
        }
        fireModulesAdded(project, listOf(module))
      }
    }
  }

  private fun fireEventAndDisposeModule(module: ModuleBridge) {
    project.messageBus.syncPublisher(ModuleListener.TOPIC).moduleRemoved(project, module)
    Disposer.dispose(module)
  }

  private fun fireBeforeModuleRemoved(module: ModuleBridge) {
    project.messageBus.syncPublisher(ModuleListener.TOPIC).beforeModuleRemoved(project, module)
  }

  companion object {
    private val LOG = logger<LegacyProjectModelListenersBridge>()
  }
}
