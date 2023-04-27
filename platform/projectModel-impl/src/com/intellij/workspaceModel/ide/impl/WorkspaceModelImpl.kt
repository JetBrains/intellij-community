// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer
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
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import kotlin.system.measureTimeMillis

open class WorkspaceModelImpl(private val project: Project) : WorkspaceModel, Disposable {
  @Volatile
  var loadedFromCache = false
    protected set

  final override val entityStorage: VersionedEntityStorageImpl
  private val unloadedEntitiesStorage: VersionedEntityStorageImpl

  override val currentSnapshot: EntityStorageSnapshot
    get() = entityStorage.current

  val entityTracer: EntityTracingLogger = EntityTracingLogger()

  var userWarningLoggingLevel = false
    @TestOnly set

  private val updateModelMethodName = WorkspaceModelImpl::updateProjectModel.name
  private val updateModelSilentMethodName = WorkspaceModelImpl::updateProjectModelSilent.name
  private val onChangedMethodName = WorkspaceModelImpl::onChanged.name

  init {
    log.debug { "Loading workspace model" }

    val initialContent = WorkspaceModelInitialTestContent.pop()
    val cache = WorkspaceModelCache.getInstance(project)
    val (projectEntities, unloadedEntities) = when {
      initialContent != null -> {
        loadedFromCache = initialContent !== EntityStorageSnapshot.empty()
        initialContent.toBuilder() to EntityStorageSnapshot.empty()
      }
      cache != null -> {
        val activity = StartUpMeasurer.startActivity("cache loading")
        val previousStorage: MutableEntityStorage?
        val previousStorageForUnloaded: EntityStorageSnapshot
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()?.toBuilder()
          previousStorageForUnloaded = cache.loadUnloadedEntitiesCache()?.toSnapshot() ?: EntityStorageSnapshot.empty()
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
        storage to previousStorageForUnloaded
      }
      else -> MutableEntityStorage.create() to EntityStorageSnapshot.empty()
    }

    @Suppress("LeakingThis")
    prepareModel(project, projectEntities)

    entityStorage = VersionedEntityStorageImpl(projectEntities.toSnapshot())
    unloadedEntitiesStorage = VersionedEntityStorageImpl(unloadedEntities)
    entityTracer.subscribe(project)
  }

  override val currentSnapshotOfUnloadedEntities: EntityStorageSnapshot
    get() = unloadedEntitiesStorage.current

  /**
   * Used only in Rider IDE
   */
  @ApiStatus.Internal
  open fun prepareModel(project: Project, storage: MutableEntityStorage) = Unit

  fun ignoreCache() {
    loadedFromCache = false
  }

  final override fun updateProjectModel(description: @NonNls String, updater: (MutableEntityStorage) -> Unit) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    checkRecursiveUpdate()

    val updateTimeMillis: Long
    val preHandlersTimeMillis: Long
    val collectChangesTimeMillis: Long
    val initializingTimeMillis: Long
    val toSnapshotTimeMillis: Long
    val generalTime = measureTimeMillis {
      val before = entityStorage.current
      val builder = MutableEntityStorage.from(before)
      updateTimeMillis = measureTimeMillis {
        updater(builder)
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
  }

  /**
   * Update project model without the notification to message bus and without resetting accumulated changes.
   *
   * This method doesn't require write action.
   */
  @Synchronized
  fun updateProjectModelSilent(description: @NonNls String, updater: (MutableEntityStorage) -> Unit) {
    checkRecursiveUpdate()

    val newStorage: EntityStorageSnapshot
    val updateTimeMillis: Long
    val toSnapshotTimeMillis: Long
    val generalTime = measureTimeMillis {
      val before = entityStorage.current
      val builder = MutableEntityStorage.from(entityStorage.current)
      updateTimeMillis = measureTimeMillis {
        updater(builder)
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
  }

  private fun checkRecursiveUpdate() {
    val stackStraceIterator = RuntimeException().stackTrace.iterator()
    // Skip two methods of the current update
    repeat(2) { stackStraceIterator.next() }
    while (stackStraceIterator.hasNext()) {
      val frame = stackStraceIterator.next()
      if ((frame.methodName == updateModelMethodName || frame.methodName == updateModelSilentMethodName)
          && frame.className == WorkspaceModelImpl::class.qualifiedName) {
        log.error("Trying to update project model twice from the same version. Maybe recursive call of 'updateProjectModel'?")
      } else if (frame.methodName == onChangedMethodName && frame.className == WorkspaceModelImpl::class.qualifiedName) {
        // It's fine to update the project method in "after update" listeners
        return
      }
    }
  }

  override fun updateUnloadedEntities(description: @NonNls String, updater: (MutableEntityStorage) -> Unit) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return

    val time = measureTimeMillis {
      val before = currentSnapshotOfUnloadedEntities
      val builder = MutableEntityStorage.from(before)
      updater(builder)
      startPreUpdateHandlers(before, builder)
      val changes = builder.collectChanges(before)
      val newStorage = builder.toSnapshot()
      unloadedEntitiesStorage.replace(newStorage, changes, ::onBeforeUnloadedEntitiesChanged, ::onUnloadedEntitiesChanged)
    }
    log.info("Unloaded entity storage updated in $time ms: $description")
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
      if (!GlobalLibraryTableBridge.isEnabled()) return@logErrorOnEventHandling
      // To handle changes made directly in project level workspace model
      (GlobalLibraryTableBridge.getInstance() as GlobalLibraryTableBridgeImpl).initializeLibraryBridges(change, builder)
    }
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
      (project.serviceIfCreated<WorkspaceFileIndex>() as? WorkspaceFileIndexImpl)?.indexData?.onEntitiesChanged(change, EntityStorageKind.MAIN)
    }

    logErrorOnEventHandling {
      project.messageBus.syncPublisher(WorkspaceModelTopics.CHANGED).changed(change)
    }
  }

  private fun onBeforeUnloadedEntitiesChanged(change: VersionedStorageChange) {
    logErrorOnEventHandling {
      project.messageBus.syncPublisher(WorkspaceModelTopics.UNLOADED_ENTITIES_CHANGED).beforeChanged(change)
    }
  }

  private fun onUnloadedEntitiesChanged(change: VersionedStorageChange) {
    //it is important to update WorkspaceFileIndex before other listeners are called because they may rely on it
    logErrorOnEventHandling {
      (project.serviceIfCreated<WorkspaceFileIndex>() as? WorkspaceFileIndexImpl)?.indexData?.onEntitiesChanged(change, EntityStorageKind.UNLOADED)
    }
    logErrorOnEventHandling {
      project.messageBus.syncPublisher(WorkspaceModelTopics.UNLOADED_ENTITIES_CHANGED).changed(change)
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