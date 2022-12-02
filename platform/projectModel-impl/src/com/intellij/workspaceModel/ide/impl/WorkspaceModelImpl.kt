// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer.startActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

open class WorkspaceModelImpl(private val project: Project) : WorkspaceModel, Disposable {
  @Volatile
  var loadedFromCache = false
    protected set

  final override val entityStorage: VersionedEntityStorageImpl
  
  override val currentSnapshot: EntityStorageSnapshot
    get() = entityStorage.current

  val entityTracer: EntityTracingLogger = EntityTracingLogger()

  var userWarningLoggingLevel = false
    @TestOnly set

  private val projectModelVersionUpdate = AtomicLong(-1)

  init {
    log.debug { "Loading workspace model" }

    val initialContent = WorkspaceModelInitialTestContent.pop()
    val cache = WorkspaceModelCache.getInstance(project)
    val projectEntities: MutableEntityStorage = when {
      initialContent != null -> {
        loadedFromCache = initialContent !== EntityStorageSnapshot.empty()
        initialContent.toBuilder()
      }
      cache != null -> {
        val activity = startActivity("cache loading")
        val previousStorage: MutableEntityStorage?
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()?.toBuilder()
        }
        val storage = if (previousStorage == null) {
          MutableEntityStorage.create()
        }
        else {
          log.info("Load workspace model from cache in $loadingCacheTime ms")
          loadedFromCache = true
          entityTracer.printInfoAboutTracedEntity(previousStorage, "cache")
          previousStorage
        }
        activity.end()
        storage
      }
      else -> MutableEntityStorage.create()
    }

    @Suppress("LeakingThis")
    prepareModel(project, projectEntities)

    entityStorage = VersionedEntityStorageImpl(projectEntities.toSnapshot())
    entityTracer.subscribe(project)
  }

  /**
   * Used only in Rider IDE
   */
  @ApiStatus.Internal
  open fun prepareModel(project: Project, storage: MutableEntityStorage) = Unit

  fun ignoreCache() {
    loadedFromCache = false
  }

  final override fun <R> updateProjectModel(description: @NonNls String, updater: (MutableEntityStorage) -> R): R {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (projectModelVersionUpdate.get() == entityStorage.pointer.version) {
      log.error("Trying to update project model twice from the same version. Maybe recursive call of 'updateProjectModel'?")
    }
    projectModelVersionUpdate.set(entityStorage.pointer.version)

    val result: R
    val updateTimeMillis: Long
    val preHandlersTimeMillis: Long
    val collectChangesTimeMillis: Long
    val initializingTimeMillis: Long
    val toSnapshotTimeMillis: Long
    val generalTime = measureTimeMillis {
      val before = entityStorage.current
      val builder = MutableEntityStorage.from(before)
      updateTimeMillis = measureTimeMillis {
        result = updater(builder)
      }
      preHandlersTimeMillis = measureTimeMillis {
        startPreUpdateHandlers(before, builder)
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
    log.info("Project model updated to version ${entityStorage.pointer.version} in $generalTime ms: $description")
    if (generalTime > 1000) {
      log.info(
        "Project model update details: Updater code: $updateTimeMillis ms, Pre handlers: $preHandlersTimeMillis ms, Collect changes: $collectChangesTimeMillis ms")
      log.info("Bridge initialization: $initializingTimeMillis ms, To snapshot: $toSnapshotTimeMillis ms")
    }
    else {
      log.debug {
        "Project model update details: Updater code: $updateTimeMillis ms, Pre handlers: $preHandlersTimeMillis ms, Collect changes: $collectChangesTimeMillis ms"
      }
      log.debug { "Bridge initialization: $initializingTimeMillis ms, To snapshot: $toSnapshotTimeMillis ms" }
    }
    return result
  }

  /**
   * Update project model without the notification to message bus and without resetting accumulated changes.
   *
   * This method doesn't require write action.
   */
  @Synchronized
  final override fun <R> updateProjectModelSilent(description: @NonNls String, updater: (MutableEntityStorage) -> R): R {
    if (projectModelVersionUpdate.get() == entityStorage.pointer.version) {
      log.error("Trying to update project model twice from the same version. Maybe recursive call of 'updateProjectModel'?")
    }
    projectModelVersionUpdate.set(entityStorage.pointer.version)

    val result: R
    val newStorage: EntityStorageSnapshot
    val updateTimeMillis: Long
    val toSnapshotTimeMillis: Long
    val generalTime = measureTimeMillis {
      val before = entityStorage.current
      val builder = MutableEntityStorage.from(entityStorage.current)
      updateTimeMillis = measureTimeMillis {
        result = updater(builder)
      }
      toSnapshotTimeMillis = measureTimeMillis {
        newStorage = builder.toSnapshot()
      }
      if (Registry.`is`("ide.workspace.model.assertions.on.update", false)) {
        before.assertConsistency()
        newStorage.assertConsistency()
      }
      entityStorage.replaceSilently(newStorage)
    }
    log.info("Project model updated silently to version ${entityStorage.pointer.version} in $generalTime ms: $description")
    if (generalTime > 1000) {
      log.info("Project model update details: Updater code: $updateTimeMillis ms, To snapshot: $toSnapshotTimeMillis m")
    }
    else {
      log.debug { "Project model update details: Updater code: $updateTimeMillis ms, To snapshot: $toSnapshotTimeMillis m" }
    }
    return result
  }

  final override fun getBuilderSnapshot(): BuilderSnapshot {
    val current = entityStorage.pointer
    return BuilderSnapshot(current.version, current.storage)
  }

  final override fun replaceProjectModel(replacement: StorageReplacement): Boolean {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    if (entityStorage.version != replacement.version) return false

    val builder = replacement.builder
    this.initializeBridges(replacement.changes, builder)
    entityStorage.replace(builder.toSnapshot(), replacement.changes, this::onBeforeChanged, this::onChanged)

    return true
  }

  final override fun dispose() = Unit

  private fun initializeBridges(change: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return
    logErrorOnEventHandling {
      (project.serviceOrNull<ProjectLibraryTable>() as? ProjectLibraryTableBridgeImpl)?.initializeLibraryBridges(change, builder)
    }
    logErrorOnEventHandling {
      (project.serviceOrNull<ModuleManager>() as? ModuleManagerBridgeImpl)?.initializeBridges(change, builder)
    }
  }

  /**
   * Order of events: initialize project libraries, initialize module bridge + module friends, all other listeners
   */
  private fun onBeforeChanged(change: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return

    logErrorOnEventHandling {
      project.messageBus.syncPublisher(WorkspaceModelTopics.CHANGED).beforeChanged(change)
    }
  }

  private fun onChanged(change: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return
    //it is important to update WorkspaceFileIndex before other listeners are called because they may rely on it
    logErrorOnEventHandling {
      (project.serviceIfCreated<WorkspaceFileIndex>() as? WorkspaceFileIndexImpl)?.onEntitiesChanged(change)
    }

    logErrorOnEventHandling {
      project.messageBus.syncPublisher(WorkspaceModelTopics.CHANGED).changed(change)
    }
  }

  private fun startPreUpdateHandlers(before: EntityStorage, builder: MutableEntityStorage) {
    var startUpdateLoop = true
    var updatesStarted = 0
    while (startUpdateLoop && updatesStarted < PRE_UPDATE_LOOP_BLOCK) {
      updatesStarted += 1
      startUpdateLoop = false
      PRE_UPDATE_HANDLERS.extensionsIfPointIsRegistered.forEach {
        startUpdateLoop = startUpdateLoop or it.update(before, builder)
      }
    }
    if (updatesStarted >= PRE_UPDATE_LOOP_BLOCK) {
      log.error("Loop workspace model updating")
    }
  }

  private fun logErrorOnEventHandling(action: () -> Unit) {
    try {
      action.invoke()
    } catch (e: Throwable) {
      val message = "Exception at Workspace Model event handling"
      if (userWarningLoggingLevel) {
        log.warn(message, e)
      } else {
        log.error(message, e)
      }
    }
  }

  companion object {
    private val log = logger<WorkspaceModelImpl>()

    private val PRE_UPDATE_HANDLERS = ExtensionPointName.create<WorkspaceModelPreUpdateHandler>("com.intellij.workspaceModel.preUpdateHandler")
    private const val PRE_UPDATE_LOOP_BLOCK = 100
  }
}
