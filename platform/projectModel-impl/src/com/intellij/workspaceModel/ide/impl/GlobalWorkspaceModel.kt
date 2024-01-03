// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.getGlobalInstance
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LegacyCustomLibraryEntitySource
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.ide.legacyBridge.GlobalEntityBridgeAndEventHandler
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridge
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

@OptIn(EntityStorageInstrumentationApi::class)
@ApiStatus.Internal
class GlobalWorkspaceModel : Disposable {
  /**
   * Store link to the project from which changes came from. It's needed to avoid redundant changes application at [applyStateToProject]
   */
  private var filteredProject: Project? = null

  // Marker indicating that changes came from global storage
  internal var isFromGlobalWorkspaceModel: Boolean = false
  private val globalWorkspaceModelCache = GlobalWorkspaceModelCache.getInstance()
  private val globalEntitiesFilter = { entitySource: EntitySource -> entitySource is JpsGlobalFileEntitySource
                                                                     || entitySource is LegacyCustomLibraryEntitySource }

  val entityStorage: VersionedEntityStorageImpl
  val currentSnapshot: ImmutableEntityStorage
    get() = entityStorage.current

  var loadedFromCache = false
    private set

  private val updateModelMethodName = GlobalWorkspaceModel::updateModel.name
  private val onChangedMethodName = GlobalWorkspaceModel::onChanged.name

  init {
    LOG.debug { "Loading global workspace model" }

    val cache = globalWorkspaceModelCache
    val mutableEntityStorage: MutableEntityStorage = when {
      cache != null -> {
        val activity = StartUpMeasurer.startActivity("global cache loading")
        val previousStorage: MutableEntityStorage?
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()
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
    entityStorage = VersionedEntityStorageImpl(ImmutableEntityStorage.empty())

    val callback = JpsGlobalModelSynchronizer.getInstance().loadInitialState(mutableEntityStorage, entityStorage, loadedFromCache)
    val changes = (mutableEntityStorage as MutableEntityStorageInstrumentation).collectChanges()
    entityStorage.replace(mutableEntityStorage.toSnapshot(), changes, {}, {})
    callback.invoke()
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  fun updateModel(description: @NonNls String, updater: (MutableEntityStorage) -> Unit) {
    ThreadingAssertions.assertWriteAccess()
    checkRecursiveUpdate(description)

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
      updatesCounter.incrementAndGet()
      totalUpdatesTimeMs.addAndGet(this)
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

  @RequiresWriteLock
  private fun initializeBridges(change: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    ThreadingAssertions.assertWriteAccess()

    GlobalEntityBridgeAndEventHandler.getAllGlobalEntityHandlers().forEach {
      logErrorOnEventHandling {
        it.initializeBridges(change, builder)
      }
    }
  }

  private fun onBeforeChanged(change: VersionedStorageChange) {
    ThreadingAssertions.assertWriteAccess()

    GlobalEntityBridgeAndEventHandler.getAllGlobalEntityHandlers().forEach { it.handleBeforeChangeEvents(change) }
  }

  @RequiresWriteLock
  private fun onChanged(change: VersionedStorageChange) {
    ThreadingAssertions.assertWriteAccess()

    GlobalEntityBridgeAndEventHandler.getAllGlobalEntityHandlers().forEach { it.handleChangedEvents(change) }

    globalWorkspaceModelCache?.scheduleCacheSave()
    isFromGlobalWorkspaceModel = true
    ProjectManager.getInstance().openProjects.forEach { project ->
      if (!project.isDisposed) applyStateToProject(project)
    }
    isFromGlobalWorkspaceModel = false
  }

  @RequiresWriteLock
  fun applyStateToProject(targetProject: Project) = applyStateToProjectTimeMs.addMeasuredTimeMillis {
    ThreadingAssertions.assertWriteAccess()

    if (targetProject === filteredProject) {
      return@addMeasuredTimeMillis
    }

    val workspaceModel = WorkspaceModel.getInstance(targetProject)
    val entitiesCopyAtBuilder = copyEntitiesToEmptyStorage(entityStorage.current,
                                                           VirtualFileUrlManager.getInstance(targetProject))
    workspaceModel.updateProjectModel("Sync global entities with project: ${targetProject.name}") { builder ->
      builder.replaceBySource(globalEntitiesFilter, entitiesCopyAtBuilder)
    }
  }

  fun applyStateToProjectBuilder(project: Project,
                                 targetBuilder: MutableEntityStorage) = applyStateToProjectBuilderTimeMs.addMeasuredTimeMillis {
    LOG.info("Sync global entities with mutable entity storage")
    targetBuilder.replaceBySource(globalEntitiesFilter,
                                  copyEntitiesToEmptyStorage(entityStorage.current, VirtualFileUrlManager.getInstance(project)))
  }

  @RequiresWriteLock
  fun syncEntitiesWithProject(sourceProject: Project) = syncEntitiesWithProjectTimeMs.addMeasuredTimeMillis {
    ThreadingAssertions.assertWriteAccess()

    filteredProject = sourceProject
    val entitiesCopyAtBuilder = copyEntitiesToEmptyStorage(WorkspaceModel.getInstance(sourceProject).currentSnapshot,
                                                           VirtualFileUrlManager.getGlobalInstance())
    updateModel("Sync entities from project ${sourceProject.name} with global storage") { builder ->
      builder.replaceBySource(globalEntitiesFilter, entitiesCopyAtBuilder)
    }
    filteredProject = null
  }

  /**
   * Things that must be considered if you'd love to change this logic: IDEA-342103
   */
  private fun checkRecursiveUpdate(description: @NonNls String) {
    val stackStraceIterator = RuntimeException().stackTrace.iterator()
    // Skip two methods of the current update
    repeat(2) { stackStraceIterator.next() }
    while (stackStraceIterator.hasNext()) {
      val frame = stackStraceIterator.next()
      if (frame.methodName == updateModelMethodName && frame.className == GlobalWorkspaceModel::class.qualifiedName) {
        LOG.error("Trying to update global model twice from the same version. Maybe recursive call of 'updateModel'? Action: $description")
      }
      else if (frame.methodName == onChangedMethodName && frame.className == GlobalWorkspaceModel::class.qualifiedName) {
        // It's fine to update the project method in "after update" listeners
        return
      }
    }
  }

  private fun copyEntitiesToEmptyStorage(storage: EntityStorage, vfuManager: VirtualFileUrlManager): MutableEntityStorage {
    val mutableEntityStorage = MutableEntityStorage.create()
    // Copying global and custom libraries
    storage.entities(LibraryEntity::class.java).forEach { libraryEntity ->
      if (!globalEntitiesFilter.invoke(libraryEntity.entitySource)) return@forEach
      val libraryRootsCopy = libraryEntity.roots.map { root ->
        LibraryRoot(root.url.createCopyAtManager(vfuManager), root.type, root.inclusionOptions)
      }

      // If it's global library then we need to copy its entity source. For the custom lib we just reuse origin entity source
      val entitySourceCopy = (libraryEntity.entitySource as? JpsGlobalFileEntitySource)?.copy(vfuManager) ?: libraryEntity.entitySource
      val excludedRootsCopy = libraryEntity.excludedRoots.map { it.copy(entitySourceCopy, vfuManager) }
      val libraryPropertiesCopy = libraryEntity.libraryProperties?.copy(entitySourceCopy)
      val libraryEntityCopy = LibraryEntity(libraryEntity.name, libraryEntity.tableId, libraryRootsCopy, entitySourceCopy) {
        excludedRoots = excludedRootsCopy
        libraryProperties = libraryPropertiesCopy
      }
      mutableEntityStorage.addEntity(libraryEntityCopy)
      val libraryBridge = storage.libraryMap.getDataByEntity(libraryEntity)
      if (libraryBridge != null) mutableEntityStorage.mutableLibraryMap.addIfAbsent(libraryEntityCopy, libraryBridge)
    }

    // If registry flag for SDK is disabled we don't need to apply its data to the storage
    if (!GlobalSdkTableBridge.isEnabled()) return mutableEntityStorage
    // Copying sdks
    storage.entities(SdkEntity::class.java).forEach { sdkEntity ->
      if (!globalEntitiesFilter.invoke(sdkEntity.entitySource)) return@forEach
      val sdkRootsCopy = sdkEntity.roots.map { root ->
        SdkRoot(root.url.createCopyAtManager(vfuManager), root.type)
      }

      val entitySourceCopy = (sdkEntity.entitySource as JpsGlobalFileEntitySource).copy(vfuManager)
      val sdkEntityCopy = SdkEntity(sdkEntity.name, sdkEntity.type, sdkRootsCopy, sdkEntity.additionalData, entitySourceCopy) {
        homePath = sdkEntity.homePath?.createCopyAtManager(vfuManager)
        version = sdkEntity.version
      }
      mutableEntityStorage.addEntity(sdkEntityCopy)
      val sdkBridge = storage.getExternalMapping(SDK_BRIDGE_MAPPING_ID).getDataByEntity(sdkEntity)
      if (sdkBridge != null) {
        mutableEntityStorage.getMutableExternalMapping(SDK_BRIDGE_MAPPING_ID).addIfAbsent(sdkEntityCopy, sdkBridge)
      }
    }
    return mutableEntityStorage
  }

  private fun logErrorOnEventHandling(action: () -> Unit) {
    try {
      action.invoke()
    }
    catch (e: Throwable) {
      val message = "Exception at Workspace Model event handling"
      LOG.error(message, e)
    }
  }

  companion object {

    //TODO:: Fix me don't have dependencies to SdkTableBridgeImpl
    private val SDK_BRIDGE_MAPPING_ID = ExternalMappingKey.create<Any>("intellij.sdk.bridge")



    private val LOG = logger<GlobalWorkspaceModel>()
    fun getInstance(): GlobalWorkspaceModel = ApplicationManager.getApplication().service()

    private val updatesCounter: AtomicLong = AtomicLong()
    private val totalUpdatesTimeMs: AtomicLong = AtomicLong()
    private val applyStateToProjectTimeMs: AtomicLong = AtomicLong()
    private val applyStateToProjectBuilderTimeMs: AtomicLong = AtomicLong()
    private val syncEntitiesWithProjectTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val updateTimesCounter = meter.counterBuilder("workspaceModel.global.updates.count").buildObserver()
      val totalUpdatesTimeCounter = meter.counterBuilder("workspaceModel.global.updates.ms").buildObserver()
      val applyStateToProjectTimeCounter = meter.counterBuilder("workspaceModel.global.apply.state.to.project.ms").buildObserver()
      val applyStateToProjectBuilderTimeCounter = meter.counterBuilder("workspaceModel.global.apply.state.to.project.builder.ms").buildObserver()
      val syncEntitiesWithProjectTimeCounter = meter.counterBuilder("workspaceModel.sync.entities.ms").buildObserver()

      meter.batchCallback(
        {
          updateTimesCounter.record(updatesCounter.get())
          totalUpdatesTimeCounter.record(totalUpdatesTimeMs.get())
          applyStateToProjectTimeCounter.record(applyStateToProjectTimeMs.get())
          applyStateToProjectBuilderTimeCounter.record(applyStateToProjectBuilderTimeMs.get())
          syncEntitiesWithProjectTimeCounter.record(syncEntitiesWithProjectTimeMs.get())
        },
        updateTimesCounter, totalUpdatesTimeCounter, applyStateToProjectTimeCounter,
        applyStateToProjectBuilderTimeCounter, syncEntitiesWithProjectTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}

private fun VirtualFileUrl.createCopyAtManager(manager: VirtualFileUrlManager): VirtualFileUrl = manager.fromUrl(url)

private fun ExcludeUrlEntity.copy(entitySource: EntitySource, manager: VirtualFileUrlManager): ExcludeUrlEntity =
  ExcludeUrlEntity(url.createCopyAtManager(manager), entitySource)

private fun LibraryPropertiesEntity.copy(entitySource: EntitySource): LibraryPropertiesEntity {
  val originalPropertiesXmlTag = propertiesXmlTag
  return LibraryPropertiesEntity(libraryType, entitySource) {
    this.propertiesXmlTag = originalPropertiesXmlTag
  }
}

private fun JpsGlobalFileEntitySource.copy(manager: VirtualFileUrlManager): JpsGlobalFileEntitySource =
  JpsGlobalFileEntitySource(file.createCopyAtManager(manager))
