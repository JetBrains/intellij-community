// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.*
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.*

/**
 * Tasks List:
 * [x] Fix synchronization after project loading from cache
 * [x] Update bridges if changes made in global storage directly
 * [x] Fix saving cache on application close
 * [x] Fix applying changes for app level libs from projects
 * [x] Write tests for this logic
 * [x] Save XML on disk on close
 * [x] Check module dependencies update after library rename
 * [x] Rework initialization to avoid preload await
 * [x] Fix entity source
 * [] Sync only entities linked to module
 * [] Rework delayed synchronize
 */

class JpsGlobalModelSynchronizerImpl: JpsGlobalModelSynchronizer {
  private var loadedFromDisk: Boolean = false

  override fun loadInitialState(mutableStorage: MutableEntityStorage, initialEntityStorage: VersionedEntityStorage,
                                loadedFromCache: Boolean): () -> Unit {
    return if (loadedFromCache) {
      GlobalLibraryTableBridge.getInstance().initializeLibraryBridgesAfterLoading(mutableStorage, initialEntityStorage)
    }
    else {
      loadGlobalEntitiesToEmptyStorage(mutableStorage, initialEntityStorage)
    }
  }

  override fun delayLoadGlobalWorkspaceModel() {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    if (globalWorkspaceModel.loadedFromCache && !loadedFromDisk) {
      val mutableStorage = MutableEntityStorage.create()

      loadGlobalEntitiesToEmptyStorage(mutableStorage, globalWorkspaceModel.entityStorage)
      globalWorkspaceModel.updateModel("Sync global entities with state") { builder ->
        builder.replaceBySource({ it is JpsFileEntitySource.ExactGlobalFile }, mutableStorage)
      }
    }
  }

  fun saveGlobalEntities(writer: JpsFileContentWriter) {
    val serializer = JpsGlobalEntitiesSerializers.createApplicationSerializers()
    val entityStorage = GlobalWorkspaceModel.getInstance().entityStorage.current
    val libraryEntities = entityStorage.entities(LibraryEntity::class.java).toList()
    if (serializer != null) {
      LOG.info("Saving global entities to files")
      serializer.saveEntities(libraryEntities, emptyMap(), entityStorage, writer)
    }
  }

  private fun loadGlobalEntitiesToEmptyStorage(mutableStorage: MutableEntityStorage, initialEntityStorage: VersionedEntityStorage): () -> Unit {
    val contentReader = (ApplicationManager.getApplication().stateStore as ApplicationStoreJpsContentReader).createContentReader()
    val serializer = JpsGlobalEntitiesSerializers.createApplicationSerializers()
    val errorReporter = object : ErrorReporter {
      override fun reportError(message: String, file: VirtualFileUrl) {
        LOG.warn(message)
      }
    }
    if (serializer != null) {
      LOG.info("Loading global entities from files")
      serializer.loadEntities(mutableStorage, contentReader, errorReporter, VirtualFileUrlManager.getGlobalInstance())
    }
    val callback = GlobalLibraryTableBridge.getInstance().initializeLibraryBridgesAfterLoading(mutableStorage, initialEntityStorage)
    loadedFromDisk = true
    return callback
  }

  companion object {
    private val LOG = logger<JpsGlobalModelSynchronizerImpl>()
  }
}