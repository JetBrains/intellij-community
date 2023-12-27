// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.*
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMillis
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

open class WorkspaceModelImpl(private val project: Project, private val cs: CoroutineScope) : WorkspaceModel, Disposable {
  @Volatile
  var loadedFromCache = false
    protected set

  final override val entityStorage: VersionedEntityStorageImpl
  private val unloadedEntitiesStorage: VersionedEntityStorageImpl

  private val updatesFlow = MutableSharedFlow<VersionedStorageChange>()

  /**
   * This flow will become obsolete, as we'll migrate to reactive listeners. However, [updatesFlow] will remain here
   *   as a building block of the new listeners.
   */
  override val changesEventFlow: Flow<VersionedStorageChange> = updatesFlow.asSharedFlow()

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
    val start = System.currentTimeMillis()

    val initialContent = WorkspaceModelInitialTestContent.pop()
    val cache = WorkspaceModelCache.getInstance(project)
    val (projectEntities, unloadedEntities) = when {
      initialContent != null -> {
        loadedFromCache = initialContent !== EntityStorageSnapshot.empty()
        initialContent.toBuilder() to EntityStorageSnapshot.empty()
      }
      cache != null -> {
        val activity = StartUpMeasurer.startActivity("cache loading")
        val cacheLoadingStart = System.currentTimeMillis()

        val previousStorage: MutableEntityStorage?
        val previousStorageForUnloaded: EntityStorageSnapshot
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()
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

        loadingFromCacheTimeMs.addElapsedTimeMillis(cacheLoadingStart)
        activity.end()
        storage to previousStorageForUnloaded
      }
      else -> MutableEntityStorage.create() to EntityStorageSnapshot.empty()
    }

    @Suppress("LeakingThis")
    prepareModel(project, projectEntities)

    entityStorage = VersionedEntityStorageImpl(projectEntities.toSnapshot())
    unloadedEntitiesStorage = VersionedEntityStorageImpl(unloadedEntities)
    entityTracer.subscribe(project, cs)
    loadingTotalTimeMs.addElapsedTimeMillis(start)
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

  @OptIn(EntityStorageInstrumentationApi::class)
  @Synchronized
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
        changes = (builder as MutableEntityStorageInstrumentation).collectChanges()
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
    }.apply {
      updateTimePreciseMs.addAndGet(updateTimeMillis)
      preHandlersTimeMs.addAndGet(preHandlersTimeMillis)
      collectChangesTimeMs.addAndGet(collectChangesTimeMillis)
      initializingTimeMs.addAndGet(initializingTimeMillis)
      toSnapshotTimeMs.addAndGet(toSnapshotTimeMillis)
      totalUpdatesTimeMs.addAndGet(this)
      updatesCounter.incrementAndGet()
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

  override suspend fun update(description: String, updater: (MutableEntityStorage) -> Unit) {
    // TODO:: Make the logic smarter and avoid using WR if there are no subscribers via topic.
    //  Right now we don't have API to check how many subscribers for the topic we have

    // In the version without write action, we'll need to replace write lock with an async mutex from the kotlin coroutines.
    ApplicationManager.getApplication().assertReadAccessNotAllowed()
    writeAction { updateProjectModel(description, updater) }
  }

  /**
   * Update project model without the notification to message bus and without resetting accumulated changes.
   *
   * This method doesn't require write action.
   *
   * This method runs without write action, so it causes different issues. We're going to deprecate this method, so it's better to avoid
   *   the use of this function.
   *
   * **N.B** For more information on why this and other methods were marked by Synchronized see IDEA-313151
   */
  @OptIn(EntityStorageInstrumentationApi::class)
  @ApiStatus.Obsolete
  @Synchronized
  fun updateProjectModelSilent(description: @NonNls String, updater: (MutableEntityStorage) -> Unit) {
    checkRecursiveUpdate()

    val newStorage: EntityStorageSnapshot
    val updateTimeMillis: Long
    val toSnapshotTimeMillis: Long

    val generalTime = measureTimeMillis {
      val before = entityStorage.current
      val builder = MutableEntityStorage.from(entityStorage.current) as MutableEntityStorageInstrumentation
      updateTimeMillis = measureTimeMillis {
        updater(builder)
      }

      // We don't send changes to the WorkspaceModelChangeListener during the silent update.
      // But the concept of silent update is getting deprecated, and the list of changes will be sent to the new async listeners
      val changes = builder.collectChanges()

      toSnapshotTimeMillis = measureTimeMillis {
        newStorage = builder.toSnapshot()
      }
      if (Registry.`is`("ide.workspace.model.assertions.on.update", false)) {
        before.assertConsistency()
        newStorage.assertConsistency()
      }
      entityStorage.replace(newStorage, changes, {}, {})
    }.apply {
      updateTimePreciseMs.addAndGet(updateTimeMillis)
      toSnapshotTimeMs.addAndGet(toSnapshotTimeMillis)
      totalUpdatesTimeMs.addAndGet(this)
      updatesCounter.incrementAndGet()
    }

    log.info("Project model updated silently to version ${entityStorage.pointer.version} in $generalTime ms: $description")
    if (generalTime > 1000) {
      log.info("Project model update details: Updater code: $updateTimeMillis ms, To snapshot: $toSnapshotTimeMillis m")
    }
    else {
      log.debug { "Project model update details: Updater code: $updateTimeMillis ms, To snapshot: $toSnapshotTimeMillis m" }
    }
  }

  private fun checkRecursiveUpdate() = checkRecursiveUpdateTimeMs.addMeasuredTimeMillis {
    val stackStraceIterator = RuntimeException().stackTrace.iterator()
    // Skip two methods of the current update
    repeat(2) { stackStraceIterator.next() }
    while (stackStraceIterator.hasNext()) {
      val frame = stackStraceIterator.next()
      if ((frame.methodName == updateModelMethodName || frame.methodName == updateModelSilentMethodName)
          && frame.className == WorkspaceModelImpl::class.qualifiedName) {
        log.error("Trying to update project model twice from the same version. Maybe recursive call of 'updateProjectModel'?")
      }
      else if (frame.methodName == onChangedMethodName && frame.className == WorkspaceModelImpl::class.qualifiedName) {
        // It's fine to update the project method in "after update" listeners
        return@addMeasuredTimeMillis
      }
    }
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun updateUnloadedEntities(description: @NonNls String, updater: (MutableEntityStorage) -> Unit) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return

    val time = measureTimeMillis {
      val before = currentSnapshotOfUnloadedEntities
      val builder = MutableEntityStorage.from(before) as MutableEntityStorageInstrumentation
      updater(builder)
      startPreUpdateHandlers(before, builder)
      val changes = builder.collectChanges()
      val newStorage = builder.toSnapshot()
      unloadedEntitiesStorage.replace(newStorage, changes, {}, ::onUnloadedEntitiesChanged)
    }.apply { updateUnloadedEntitiesTimeMs.addAndGet(this) }

    log.info("Unloaded entity storage updated in $time ms: $description")
  }

  final override fun getBuilderSnapshot(): BuilderSnapshot {
    val current = entityStorage.pointer
    return BuilderSnapshot(current.version, current.storage)
  }

  @Synchronized
  final override fun replaceProjectModel(replacement: StorageReplacement): Boolean {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    if (entityStorage.version != replacement.version) return false

    replaceProjectModelTimeMs.addMeasuredTimeMillis {
      val builder = replacement.builder
      this.initializeBridges(replacement.changes, builder)
      entityStorage.replace(builder.toSnapshot(), replacement.changes, this::onBeforeChanged, this::onChanged)
    }

    return true
  }

  final override fun dispose() = Unit

  private fun initializeBridges(change: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return

    initializeBridgesTimeMs.addMeasuredTimeMillis {
      BridgeInitializer.EP_NAME.extensionList.forEach { bridgeInitializer ->
        logErrorOnEventHandling {
          if (bridgeInitializer.isEnabled()) {
            bridgeInitializer.initializeBridges(project, change, builder)
          }
        }
      }
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
      (project.serviceIfCreated<WorkspaceFileIndex>() as? WorkspaceFileIndexImpl)?.indexData?.onEntitiesChanged(change,
                                                                                                                EntityStorageKind.MAIN)
    }

    // We emit async changes before running other listeners under write action
    cs.launch { updatesFlow.emit(change) }

    logErrorOnEventHandling {
      project.messageBus.syncPublisher(WorkspaceModelTopics.CHANGED).changed(change)
    }
  }

  private fun onUnloadedEntitiesChanged(change: VersionedStorageChange) {
    //it is important to update WorkspaceFileIndex before other listeners are called because they may rely on it
    logErrorOnEventHandling {
      (project.serviceIfCreated<WorkspaceFileIndex>() as? WorkspaceFileIndexImpl)?.indexData?.onEntitiesChanged(change,
                                                                                                                EntityStorageKind.UNLOADED)
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

  /**
   * This method executes under write lock, so we don't expect [com.intellij.openapi.progress.ProcessCanceledException]
   * and [java.util.concurrent.CancellationException] because we don't call client suspend functions.
   * But it's a different situation for [com.intellij.openapi.progress.ProcessCanceledException] even if this exception
   * occurs it's important to allow other clients to do their calculations otherwise we can get inconsistent state
   * because model was already changed.
   */
  private fun logErrorOnEventHandling(action: () -> Unit) {
    try {
      action.invoke()
    }
    catch (e: Throwable) {
      if (e is AlreadyDisposedException) throw e
      val message = "Exception at Workspace Model event handling"
      if (userWarningLoggingLevel) {
        log.warn(message, e)
      }
      else {
        log.error(message, e)
      }
    }
  }

  companion object {
    private val log = logger<WorkspaceModelImpl>()

    private val PRE_UPDATE_HANDLERS = ExtensionPointName.create<WorkspaceModelPreUpdateHandler>(
      "com.intellij.workspaceModel.preUpdateHandler")
    private const val PRE_UPDATE_LOOP_BLOCK = 100

    private val loadingTotalTimeMs: AtomicLong = AtomicLong()
    private val loadingFromCacheTimeMs: AtomicLong = AtomicLong()
    private val updatesCounter: AtomicLong = AtomicLong()

    private val updateTimePreciseMs: AtomicLong = AtomicLong()
    private val preHandlersTimeMs: AtomicLong = AtomicLong()
    private val collectChangesTimeMs: AtomicLong = AtomicLong()
    private val initializingTimeMs: AtomicLong = AtomicLong()
    private val toSnapshotTimeMs: AtomicLong = AtomicLong()
    private val totalUpdatesTimeMs: AtomicLong = AtomicLong()

    private val checkRecursiveUpdateTimeMs: AtomicLong = AtomicLong()
    private val updateUnloadedEntitiesTimeMs: AtomicLong = AtomicLong()
    private val replaceProjectModelTimeMs: AtomicLong = AtomicLong()
    private val initializeBridgesTimeMs: AtomicLong = AtomicLong()

    /**
     * This setup is in static part because meters will not be collected if the same instrument (gauge, counter ...) are registered more than once.
     * In that case WARN by OpenTelemetry will be logged 'Instrument XYZ has recorded multiple values for the same attributes.'
     * https://github.com/airbytehq/airbyte-platform/pull/213/files
     */
    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadingTotalCounter = meter.counterBuilder("workspaceModel.loading.total.ms").buildObserver()
      val loadingFromCacheCounter = meter.counterBuilder("workspaceModel.loading.from.cache.ms").buildObserver()
      val updatesTimesCounter = meter.counterBuilder("workspaceModel.updates.count").buildObserver()
      val updateTimePreciseCounter = meter.counterBuilder("workspaceModel.updates.precise.ms").buildObserver()
      val preHandlersTimeCounter = meter.counterBuilder("workspaceModel.pre.handlers.ms").buildObserver()
      val collectChangesTimeCounter = meter.counterBuilder("workspaceModel.collect.changes.ms").buildObserver()
      val initializingTimeCounter = meter.counterBuilder("workspaceModel.initializing.ms").buildObserver()
      val toSnapshotTimeCounter = meter.counterBuilder("workspaceModel.to.snapshot.ms").buildObserver()
      val totalUpdatesTimeCounter = meter.counterBuilder("workspaceModel.updates.ms").buildObserver()
      val checkRecursiveUpdateTimeCounter = meter.counterBuilder("workspaceModel.check.recursive.update.ms").buildObserver()
      val updateUnloadedEntitiesTimeCounter = meter.counterBuilder("workspaceModel.update.unloaded.entities.ms").buildObserver()
      val replaceProjectModelTimeCounter = meter.counterBuilder("workspaceModel.replace.project.model.ms").buildObserver()
      val initializeBridgesTimeCounter = meter.counterBuilder("workspaceModel.init.bridges.ms").buildObserver()

      meter.batchCallback(
        {
          loadingTotalCounter.record(loadingTotalTimeMs.get())
          loadingFromCacheCounter.record(loadingFromCacheTimeMs.get())
          updatesTimesCounter.record(updatesCounter.get())

          updateTimePreciseCounter.record(updateTimePreciseMs.get())
          preHandlersTimeCounter.record(preHandlersTimeMs.get())
          collectChangesTimeCounter.record(collectChangesTimeMs.get())
          initializingTimeCounter.record(initializingTimeMs.get())
          toSnapshotTimeCounter.record(toSnapshotTimeMs.get())
          totalUpdatesTimeCounter.record(totalUpdatesTimeMs.get())

          checkRecursiveUpdateTimeCounter.record(checkRecursiveUpdateTimeMs.get())
          updateUnloadedEntitiesTimeCounter.record(updateUnloadedEntitiesTimeMs.get())
          replaceProjectModelTimeCounter.record(replaceProjectModelTimeMs.get())
          initializeBridgesTimeCounter.record(initializeBridgesTimeMs.get())
        },
        loadingTotalCounter, loadingFromCacheCounter, updatesTimesCounter,
        updateTimePreciseCounter, preHandlersTimeCounter, collectChangesTimeCounter,
        initializingTimeCounter, toSnapshotTimeCounter, totalUpdatesTimeCounter,
        checkRecursiveUpdateTimeCounter, updateUnloadedEntitiesTimeCounter,
        replaceProjectModelTimeCounter, initializeBridgesTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(workspaceModelMetrics.meter)
    }
  }
}