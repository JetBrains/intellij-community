// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.*
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.diagnostic.telemetry.helpers.Milliseconds
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.query.Diff
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.CollectionQuery
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.messages.impl.MessageBusImpl
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.impl.reactive.WmReactive
import com.intellij.workspaceModel.ide.isCaseSensitive
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

private val EP_NAME: ExtensionPointName<BridgeInitializer> = ExtensionPointName("com.intellij.workspace.bridgeInitializer")

@ApiStatus.Internal
open class WorkspaceModelImpl : WorkspaceModelInternal {
  private val project: Project
  private val coroutineScope: CoroutineScope

  @Volatile
  var loadedFromCache: Boolean = false
    protected set

  private val reactive = WmReactive(this)

  final override val entityStorage: VersionedEntityStorageImpl
  private val unloadedEntitiesStorage: VersionedEntityStorageImpl

  /** replay = 1 is needed to send the very first state when the subscription fo the flow happens.
       otherwise, the flow won't be emitted till the first update. Since we don't update the workspace model really often,
       this may cause some unwanted delays for subscribers.
     This is used in the [eventLog] method, where we send the first version of the storage
       right after the subscription.
     However, this means that this flow will keep two storages in the flow: the old and the new. This should be okay
       since the storage is an effective structure, however, if this causes memory problems, we can switch to
       replay = 0. In this case, no extra storage will be saved, but the event will be emitted after the first
       update of the WorkspaceModel, what is probably also okay.
  */
  private val updatesFlow = MutableSharedFlow<VersionedStorageChange>(replay = 1)

  // stored property
  override val modificationTracker: ModificationTracker = ModificationTracker { entityStorage.version }

  val virtualFileManager: VirtualFileUrlManager

  override val currentSnapshot: ImmutableEntityStorage
    get() = entityStorage.current

  val entityTracer: EntityTracingLogger = EntityTracingLogger()

  var userWarningLoggingLevel: Boolean = false
    @TestOnly set

  private val updateModelMethodName = WorkspaceModelImpl::updateProjectModel.name
  private val updateModelSilentMethodName = WorkspaceModelImpl::updateProjectModelSilent.name
  private val onChangedMethodName = WorkspaceModelImpl::onChanged.name
  
  constructor(project: Project, cs: CoroutineScope, storage: ImmutableEntityStorage, virtualFileUrlManager: VirtualFileUrlManager) {
    this.project = project
    this.coroutineScope = cs
    this.virtualFileManager = virtualFileUrlManager
    entityStorage = VersionedEntityStorageImpl(storage)
    unloadedEntitiesStorage = VersionedEntityStorageImpl(ImmutableEntityStorage.empty())
    this.loadedFromCache = true
  }

  constructor(project: Project, cs: CoroutineScope) {
    this.project = project
    this.coroutineScope = cs
    this.virtualFileManager = IdeVirtualFileUrlManagerImpl(project.isCaseSensitive)
    log.debug { "Loading workspace model" }
    val start = Milliseconds.now()

    if (Registry.`is`("ide.workspace.model.assertions.on.long.listeners", true)) {
      (project.messageBus as MessageBusImpl).addMessageDeliveryListener(WorkspaceModelMessageDeliveryListener)
    }
    val initialContent = WorkspaceModelInitialTestContent.pop()
    val cache = WorkspaceModelCache.getInstance(project)?.apply { setVirtualFileUrlManager(virtualFileManager) }
    val (projectEntities, unloadedEntities) = when {
      initialContent != null -> {
        loadedFromCache = initialContent !== ImmutableEntityStorage.empty()
        initialContent.toBuilder() to ImmutableEntityStorage.empty()
      }
      cache != null -> {
        val activity = StartUpMeasurer.startActivity("cache loading")
        val cacheLoadingStart = Milliseconds.now()

        val previousStorage: MutableEntityStorage?
        val previousStorageForUnloaded: ImmutableEntityStorage
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()
          previousStorageForUnloaded = cache.loadUnloadedEntitiesCache()?.toSnapshot() ?: ImmutableEntityStorage.empty()
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

        loadingFromCacheTimeMs.addElapsedTime(cacheLoadingStart)
        activity.end()
        storage to previousStorageForUnloaded
      }
      else -> MutableEntityStorage.create() to ImmutableEntityStorage.empty()
    }

    @Suppress("LeakingThis")
    prepareModel(project, projectEntities)

    entityStorage = VersionedEntityStorageImpl(projectEntities.toSnapshot())
    unloadedEntitiesStorage = VersionedEntityStorageImpl(unloadedEntities)
    entityTracer.subscribe(project, cs)
    loadingTotalTimeMs.addElapsedTime(start)
  }

  override val currentSnapshotOfUnloadedEntities: ImmutableEntityStorage
    get() = unloadedEntitiesStorage.current

  override fun getVirtualFileUrlManager(): VirtualFileUrlManager = virtualFileManager

  /**
   * Used only in Rider IDE
   */
  @ApiStatus.Internal
  open fun prepareModel(project: Project, storage: MutableEntityStorage): Unit = Unit

  fun ignoreCache() {
    loadedFromCache = false
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  @Synchronized
  final override fun updateProjectModel(description: @NonNls String, updater: (MutableEntityStorage) -> Unit) {
    ThreadingAssertions.assertWriteAccess()
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

      val newStorage: ImmutableEntityStorage
      toSnapshotTimeMillis = measureTimeMillis {
        newStorage = builder.toSnapshot()
      }
      if (Registry.`is`("ide.workspace.model.assertions.on.update", false)) {
        before.assertConsistency()
        newStorage.assertConsistency()
      }
      entityStorage.replace(newStorage, changes, this::onBeforeChanged, this::onChanged)
    }.apply {
      updateTimePreciseMs.duration.addAndGet(updateTimeMillis)
      preHandlersTimeMs.duration.addAndGet(preHandlersTimeMillis)
      collectChangesTimeMs.duration.addAndGet(collectChangesTimeMillis)
      initializingTimeMs.duration.addAndGet(initializingTimeMillis)
      toSnapshotTimeMs.duration.addAndGet(toSnapshotTimeMillis)
      totalUpdatesTimeMs.duration.addAndGet(this)
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
    // TODO:: Has to be migrated to the implementation without WA. See IDEA-336937
    edtWriteAction { updateProjectModel(description, updater) }
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

    val newStorage: ImmutableEntityStorage
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
      updateTimePreciseMs.duration.addAndGet(updateTimeMillis)
      toSnapshotTimeMs.duration.addAndGet(toSnapshotTimeMillis)
      totalUpdatesTimeMs.duration.addAndGet(this)
      updatesCounter.incrementAndGet()
    }

    log.debug("Project model updated silently to version ${entityStorage.pointer.version} in $generalTime ms: $description")
    if (generalTime > 1000) {
      log.info("Project model update details: Updater code: $updateTimeMillis ms, To snapshot: $toSnapshotTimeMillis m")
    }
    else {
      log.debug { "Project model update details: Updater code: $updateTimeMillis ms, To snapshot: $toSnapshotTimeMillis m" }
    }
  }

  /**
   * Things that must be considered if you'd love to change this logic: IDEA-342103
   */
  private fun checkRecursiveUpdate() = checkRecursiveUpdateTimeMs.addMeasuredTime {
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
        return@addMeasuredTime
      }
    }
  }

  /**
   * The current implementation of [updatesFlow] will suspend the flow in case of the slow consumers.
   *   This, in turn, will suspend the update of the workspace model. This issue may happen even in a single consumer is slow.
   * This approach is fine for the moment as we don't have a lot of workspace model updates and the consumers are fast enough.
   *   However, this may be risky in case the number of updates will grow and the number of uncontrolled (third-party) consumers will
   *   also grow.
   *
   * Another unpleasant issue is that any bad-designed plugin may affect the update performance of the whole application. E.g.
   * ```
   * workspaceModel.eventLog.collect { delay(5.minutes) }
   * ```
   * will eventually slow down the whole application when the [updatesFlow] buffer will be fully filled.
   *
   * If this issue is faced, this event log can be refactored in the way Fleet does it: Instead of suspending the flow on buffer overflow,
   *   it can drop the old events. And instead of sending the [VersionedStorageChange] itself, it can be wrapped with event types like
   *   `FIRST`, `NEXT`, and `RESET`. The `FIRST` event corresponds to the first state of the storage, the `NEXT` is the update, and the
   *   `RESET` is emitted when the events are dropped on the overflow:
   *
   * ```kotlin
   * flow {
   *   updatesFlow.fold(null) { prevVersion, event ->
   *     if (prevVersion == null) {
   *       emit(FIRST(event.storageAfter))
   *     } else if (prevVersion + 1 == event.version) {
   *       emit(NEXT(event))
   *     } else {
   *       emit(RESET(event.storageAfter))
   *     }
   *     return@fold event.version
   *   }
   * }
   * ```
   *
   * However, this approach will force all consumers to handle three types of events. This is why
   *   it's not used at the moment.
   */
  override val eventLog: Flow<VersionedStorageChange> = updatesFlow.asSharedFlow()

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun updateUnloadedEntities(description: @NonNls String, updater: (MutableEntityStorage) -> Unit) {
    ThreadingAssertions.assertWriteAccess()
    if (project.isDisposed) return

    val time = measureTimeMillis {
      val before = currentSnapshotOfUnloadedEntities
      val builder = MutableEntityStorage.from(before) as MutableEntityStorageInstrumentation
      updater(builder)
      startPreUpdateHandlers(before, builder)
      val changes = builder.collectChanges()
      val newStorage = builder.toSnapshot()
      unloadedEntitiesStorage.replace(newStorage, changes, {}, ::onUnloadedEntitiesChanged)
    }.apply { updateUnloadedEntitiesTimeMs.duration.addAndGet(this) }

    log.info("Unloaded entity storage updated in $time ms: $description")
  }

  final override fun getBuilderSnapshot(): BuilderSnapshot {
    val current = entityStorage.pointer
    return BuilderSnapshot(current.version, current.storage)
  }

  fun getUnloadBuilderSnapshot(): BuilderSnapshot {
    val current = unloadedEntitiesStorage.pointer
    return BuilderSnapshot(current.version, current.storage)
  }

  @Synchronized
  final override fun replaceWorkspaceModel(description: @NonNls String, replacement: StorageReplacement): Boolean {
    ThreadingAssertions.assertWriteAccess()

    if (entityStorage.version != replacement.version) return false

    replaceProjectModelTimeMs.addMeasuredTime {
      val builder = replacement.builder
      this.initializeBridges(replacement.changes, builder)
      entityStorage.replace(builder.toSnapshot(), replacement.changes, this::onBeforeChanged, this::onChanged)
      log.info("Project model updated to version ${entityStorage.pointer.version}: $description")
    }
    return true
  }

  @Synchronized
  fun replaceProjectModel(mainStorageReplacement: StorageReplacement, unloadStorageReplacement: StorageReplacement): Boolean {
    ThreadingAssertions.assertWriteAccess()

    if (entityStorage.version != mainStorageReplacement.version || unloadedEntitiesStorage.version != unloadStorageReplacement.version) {
      return false
    }

    fullReplaceProjectModelTimeMs.addMeasuredTime {
      val builder = mainStorageReplacement.builder
      this.initializeBridges(mainStorageReplacement.changes, builder)
      entityStorage.replace(builder.toSnapshot(), mainStorageReplacement.changes, this::onBeforeChanged, this::onChanged)

      val unloadBuilder = unloadStorageReplacement.builder
      unloadedEntitiesStorage.replace(unloadBuilder.toSnapshot(), unloadStorageReplacement.changes, {}, ::onUnloadedEntitiesChanged)
      log.info("Project model updated to version ${entityStorage.pointer.version}")
    }
    return true
  }

  override suspend fun <T> flowOfQuery(query: StorageQuery<T>): Flow<T> = reactive.flowOfQuery(query)
  override suspend fun <T> flowOfNewElements(query: CollectionQuery<T>): Flow<T> = reactive.flowOfNewElements(query)
  override suspend fun <T> flowOfDiff(query: CollectionQuery<T>): Flow<Diff<T>> = reactive.flowOfDiff(query)

  private fun initializeBridges(change: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    if (project.isDisposed) {
      return
    }

    initializeBridgesTimeMs.addMeasuredTime {
      for (bridgeInitializer in EP_NAME.extensionList) {
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
    ThreadingAssertions.assertWriteAccess()
    if (project.isDisposed) return

    onBeforeChangedTimeMs.addMeasuredTime {
      logErrorOnEventHandling {
        project.messageBus.syncPublisher(WorkspaceModelTopics.CHANGED).beforeChanged(change)
      }
    }
  }

  private fun onChanged(change: VersionedStorageChange) {
    ThreadingAssertions.assertWriteAccess()
    if (project.isDisposed) return
    //it is important to update WorkspaceFileIndex before other listeners are called because they may rely on it
    logErrorOnEventHandling {
      (project.serviceIfCreated<WorkspaceFileIndex>() as? WorkspaceFileIndexImpl)?.indexData?.onEntitiesChanged(change,
                                                                                                                EntityStorageKind.MAIN)
    }

    // We emit async changes before running other listeners under write action
    coroutineScope.launch { updatesFlow.emit(change) }

    onChangedTimeMs.addMeasuredTime { // Measure only the time of WorkspaceModelChangeListener
      logErrorOnEventHandling {
        project.messageBus.syncPublisher(WorkspaceModelTopics.CHANGED).changed(change)
      }
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
      if (e is ControlFlowException) throw e // Control flow exceptions should never be logger, only rethrown. Related: IJPL-155938
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

    private val PRE_UPDATE_HANDLERS = ExtensionPointName<WorkspaceModelPreUpdateHandler>("com.intellij.workspaceModel.preUpdateHandler")
    private const val PRE_UPDATE_LOOP_BLOCK = 100

    private val loadingTotalTimeMs = MillisecondsMeasurer()
    private val loadingFromCacheTimeMs = MillisecondsMeasurer()
    private val updatesCounter: AtomicLong = AtomicLong()

    private val updateTimePreciseMs = MillisecondsMeasurer()
    private val preHandlersTimeMs = MillisecondsMeasurer()
    private val collectChangesTimeMs = MillisecondsMeasurer()
    private val initializingTimeMs = MillisecondsMeasurer()
    private val toSnapshotTimeMs = MillisecondsMeasurer()
    private val totalUpdatesTimeMs = MillisecondsMeasurer()

    private val checkRecursiveUpdateTimeMs = MillisecondsMeasurer()
    private val updateUnloadedEntitiesTimeMs = MillisecondsMeasurer()
    private val replaceProjectModelTimeMs = MillisecondsMeasurer()
    private val fullReplaceProjectModelTimeMs = MillisecondsMeasurer()
    private val initializeBridgesTimeMs = MillisecondsMeasurer()

    private val onBeforeChangedTimeMs = MillisecondsMeasurer()
    private val onChangedTimeMs = MillisecondsMeasurer()

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
      val fullReplaceProjectModelTimeCounter = meter.counterBuilder("workspaceModel.full.replace.project.model.ms").buildObserver()
      val initializeBridgesTimeCounter = meter.counterBuilder("workspaceModel.init.bridges.ms").buildObserver()
      val onBeforeChangedTimeCounter = meter.counterBuilder("workspaceModel.on.before.changed.ms").buildObserver()
      val onChangedTimeCounter = meter.counterBuilder("workspaceModel.on.changed.ms").buildObserver()

      meter.batchCallback(
        {
          loadingTotalCounter.record(loadingTotalTimeMs.asMilliseconds())
          loadingFromCacheCounter.record(loadingFromCacheTimeMs.asMilliseconds())
          updatesTimesCounter.record(updatesCounter.get())

          updateTimePreciseCounter.record(updateTimePreciseMs.asMilliseconds())
          preHandlersTimeCounter.record(preHandlersTimeMs.asMilliseconds())
          collectChangesTimeCounter.record(collectChangesTimeMs.asMilliseconds())
          initializingTimeCounter.record(initializingTimeMs.asMilliseconds())
          toSnapshotTimeCounter.record(toSnapshotTimeMs.asMilliseconds())
          totalUpdatesTimeCounter.record(totalUpdatesTimeMs.asMilliseconds())

          checkRecursiveUpdateTimeCounter.record(checkRecursiveUpdateTimeMs.asMilliseconds())
          updateUnloadedEntitiesTimeCounter.record(updateUnloadedEntitiesTimeMs.asMilliseconds())
          replaceProjectModelTimeCounter.record(replaceProjectModelTimeMs.asMilliseconds())
          fullReplaceProjectModelTimeCounter.record(fullReplaceProjectModelTimeMs.asMilliseconds())
          initializeBridgesTimeCounter.record(initializeBridgesTimeMs.asMilliseconds())

          onBeforeChangedTimeCounter.record(onBeforeChangedTimeMs.asMilliseconds())
          onChangedTimeCounter.record(onChangedTimeMs.asMilliseconds())
        },
        loadingTotalCounter, loadingFromCacheCounter, updatesTimesCounter,
        updateTimePreciseCounter, preHandlersTimeCounter, collectChangesTimeCounter,
        initializingTimeCounter, toSnapshotTimeCounter, totalUpdatesTimeCounter,
        checkRecursiveUpdateTimeCounter, updateUnloadedEntitiesTimeCounter,
        replaceProjectModelTimeCounter, fullReplaceProjectModelTimeCounter, initializeBridgesTimeCounter,
        onBeforeChangedTimeCounter, onChangedTimeCounter,
      )
    }

    init {
      setupOpenTelemetryReporting(workspaceModelMetrics.meter)
    }
  }
}