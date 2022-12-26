// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

@ApiStatus.Internal
class GlobalWorkspaceModel: Disposable {
  private var filteredProject: Project? = null
  internal var isFromGlobalWorkspaceModel: Boolean = false
  private val globalWorkspaceModelCache = GlobalWorkspaceModelCache.getInstance()
  private val globalEntitiesFilter = { entitySource: EntitySource -> entitySource is JpsFileEntitySource.ExactGlobalFile }

  val entityStorage: VersionedEntityStorageImpl
  val currentSnapshot: EntityStorageSnapshot
    get() = entityStorage.current

  var loadedFromCache = false
    private set

  private val modelVersionUpdate = AtomicLong(-1)

  init {
    LOG.debug { "Loading global workspace model" }

    val cache = globalWorkspaceModelCache
    val mutableEntityStorage: MutableEntityStorage = when {
      cache != null -> {
        val activity = StartUpMeasurer.startActivity("global cache loading")
        val previousStorage: MutableEntityStorage?
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()?.toBuilder()
        }
        val storage = if (previousStorage == null) {
          MutableEntityStorage.create()
        }
        else {
          LOG.info("Load global workspace model from cache in $loadingCacheTime ms")
          loadedFromCache = true
          previousStorage
        }
        activity.end()
        storage
      }
      else -> MutableEntityStorage.create()
    }
    entityStorage = VersionedEntityStorageImpl(EntityStorageSnapshot.empty())

    val callback = if (ApplicationManager.getApplication()::class.qualifiedName != "com.intellij.openapi.application.impl.ServerApplication"){
      JpsGlobalModelSynchronizer.getInstance().loadInitialState(mutableEntityStorage, entityStorage, loadedFromCache)
    } else null
    entityStorage.replaceSilently(mutableEntityStorage.toSnapshot())
    callback?.invoke()
  }

  fun updateModel(description: @NonNls String, updater: (MutableEntityStorage) -> Unit) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (modelVersionUpdate.get() == entityStorage.pointer.version) {
      LOG.error("Trying to update global model twice from the same version. Maybe recursive call of 'updateModel'?")
    }
    modelVersionUpdate.set(entityStorage.pointer.version)

    val updateTimeMillis: Long
    val collectChangesTimeMillis: Long
    val initializingTimeMillis: Long
    val toSnapshotTimeMillis: Long
    val generalTime = measureTimeMillis {
      val before = entityStorage.current
      val builder = MutableEntityStorage.from(before)
      updateTimeMillis = measureTimeMillis {
        updater(builder)
      }
      val changes: Map<Class<*>, List<EntityChange<*>>>
      collectChangesTimeMillis = measureTimeMillis {
        changes = builder.collectChanges(before)
      }
      initializingTimeMillis = measureTimeMillis {
        this.initializeBridges(changes, builder)
      }

      val newStorage: EntityStorageSnapshot
      toSnapshotTimeMillis = measureTimeMillis {
        newStorage = builder.toSnapshot()
      }
      if (Registry.`is`("ide.workspace.model.assertions.on.update", false)) {
        before.assertConsistency()
        newStorage.assertConsistency()
      }
      entityStorage.replace(newStorage, changes, this::onBeforeChanged, this::onChanged)
    }
    LOG.info("Global model updated to version ${entityStorage.pointer.version} in $generalTime ms: $description")
    if (generalTime > 1000) {
      LOG.info("Global model update details: Updater code: $updateTimeMillis ms, Collect changes: $collectChangesTimeMillis ms")
      LOG.info("Bridge initialization: $initializingTimeMillis ms, To snapshot: $toSnapshotTimeMillis ms")
    }
    else {
      LOG.debug { "Global model update details: Updater code: $updateTimeMillis ms, Collect changes: $collectChangesTimeMillis ms" }
      LOG.debug { "Bridge initialization: $initializingTimeMillis ms, To snapshot: $toSnapshotTimeMillis ms" }
    }
  }

  override fun dispose() = Unit

  private fun initializeBridges(change: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    logErrorOnEventHandling {
      GlobalLibraryTableBridge.getInstance().initializeLibraryBridges(change, builder)
    }
  }

  private fun onBeforeChanged(change: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    GlobalLibraryTableBridge.getInstance().handleBeforeChangeEvents(change)
  }

  private fun onChanged(change: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    GlobalLibraryTableBridge.getInstance().handleChangedEvents(change)
    globalWorkspaceModelCache?.scheduleCacheSave()
    isFromGlobalWorkspaceModel = true
    ProjectManager.getInstance().openProjects.forEach(::applyStateToProject)
    isFromGlobalWorkspaceModel = false
  }

  fun applyStateToProject(targetProject: Project) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    if (targetProject.isDefault || targetProject.isDisposed || targetProject === filteredProject) return
    LOG.info("Sync global entities with project: ${targetProject.name}")
    WorkspaceModel.getInstance(targetProject).updateProjectModel("Apply entities from global storage") { builder ->
      builder.replaceBySource(globalEntitiesFilter, entityStorage.current)
    }
  }

  fun applyStateToBuilder(targetBuilder: MutableEntityStorage) {
    LOG.info("Sync global entities with mutable entity storage")
    targetBuilder.replaceBySource(globalEntitiesFilter, entityStorage.current)
  }

  fun syncEntitiesWithProject(sourceProject: Project) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    filteredProject = sourceProject
    LOG.info("Apply changes from project: ${sourceProject.name}")
    updateModel("Sync entities with global storage") { builder ->
      builder.replaceBySource(globalEntitiesFilter, WorkspaceModel.getInstance(sourceProject).entityStorage.current)
    }
    filteredProject = null
  }

  private fun logErrorOnEventHandling(action: () -> Unit) {
    try {
      action.invoke()
    } catch (e: Throwable) {
      val message = "Exception at Workspace Model event handling"
      LOG.error(message, e)
    }
  }

  companion object {
    private val LOG = logger<GlobalWorkspaceModel>()
    fun getInstance(): GlobalWorkspaceModel = ApplicationManager.getApplication().service()
  }
}
