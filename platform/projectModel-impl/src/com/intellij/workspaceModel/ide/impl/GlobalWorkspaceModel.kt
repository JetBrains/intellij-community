// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.GlobalStorageEntitySource
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
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.mutableSdkMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.GlobalEntityBridgeAndEventHandler
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

@OptIn(EntityStorageInstrumentationApi::class)
@ApiStatus.Internal
class GlobalWorkspaceModel internal constructor(
  /**
   * Despite the prefix `Global`, the IDE can have multiple workspace models per isolated environment, such as WSL and Docker containers.
   *
   * Logically, the entities existing within one environment have no sense on the other environment (i.e., the files in Docker are unreachable from the host OS);
   * hence we need to:
   * 1. Prevent entities from one environment from appearing for another one;
   * 2. Ensure that the namespace of "global" entities (such as SDKs and global libraries) is local to each environment.
   */
  private val eelDescriptor: EelDescriptor,
  private val internalEnvironmentName: GlobalWorkspaceModelCache.InternalEnvironmentName,
)  {

  /**
   * Store link to the project from which changes came from. It's needed to avoid redundant changes application at [applyStateToProject]
   */
  private var filteredProject: Project? = null

  // Marker indicating that changes came from global storage
  internal var isFromGlobalWorkspaceModel: Boolean = false
  private var virtualFileManager: VirtualFileUrlManager = IdeVirtualFileUrlManagerImpl()
  private val globalWorkspaceModelCache = GlobalWorkspaceModelCache.getInstance()?.apply {
    setVirtualFileUrlManager(virtualFileManager)
    registerCachePartition(internalEnvironmentName)
  }
  private val globalEntitiesFilter = { entitySource: EntitySource -> entitySource is GlobalStorageEntitySource }

  val entityStorage: VersionedEntityStorageImpl
  val currentSnapshot: ImmutableEntityStorage
    get() = entityStorage.current

  var loadedFromCache: Boolean = false
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
          previousStorage = cache.loadCache(internalEnvironmentName)
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

    val callback = JpsGlobalModelSynchronizer.getInstance()
      .apply { setVirtualFileUrlManager(virtualFileManager) }
      .loadInitialState(internalEnvironmentName, mutableEntityStorage, entityStorage, loadedFromCache)
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
      totalUpdatesTimeMs.duration.addAndGet(this)
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

  /**
   * Returns instance of [VirtualFileUrlManager] which should be used to create [VirtualFileUrl] instances to be stored in entities added in
   * the global application-level storage.
   * It's important not to use this function for entities stored in the main [WorkspaceModel][WorkspaceModel]
   * storage, because this would create a memory leak: these instances won't be removed when the project is closed.
   */
  @ApiStatus.Internal
  fun getVirtualFileUrlManager(): VirtualFileUrlManager = virtualFileManager

  @TestOnly
  fun resetVirtualFileUrlManager() {
    virtualFileManager = IdeVirtualFileUrlManagerImpl()
    globalWorkspaceModelCache?.setVirtualFileUrlManager(virtualFileManager)
  }

  @RequiresWriteLock
  private fun initializeBridges(change: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    ThreadingAssertions.assertWriteAccess()

    GlobalEntityBridgeAndEventHandler.getAllGlobalEntityHandlers(eelDescriptor).forEach {
      logErrorOnEventHandling {
        it.initializeBridges(change, builder)
      }
    }
  }

  private fun onBeforeChanged(change: VersionedStorageChange) {
    ThreadingAssertions.assertWriteAccess()

    GlobalEntityBridgeAndEventHandler.getAllGlobalEntityHandlers(eelDescriptor).forEach { it.handleBeforeChangeEvents(change) }
  }

  @RequiresWriteLock
  private fun onChanged(change: VersionedStorageChange) {
    ThreadingAssertions.assertWriteAccess()

    GlobalEntityBridgeAndEventHandler.getAllGlobalEntityHandlers(eelDescriptor).forEach { it.handleChangedEvents(change) }

    globalWorkspaceModelCache?.scheduleCacheSave()
    isFromGlobalWorkspaceModel = true
    for (project in ProjectManager.getInstance().openProjects) {
      if (project.isDisposed || project.getEelDescriptor() != eelDescriptor) {
        continue
      }
      applyStateToProject(project)
    }
    isFromGlobalWorkspaceModel = false
  }

  @RequiresWriteLock
  fun applyStateToProject(targetProject: Project): Unit = applyStateToProjectTimeMs.addMeasuredTime {
    ThreadingAssertions.assertWriteAccess()

    if (targetProject === filteredProject) {
      return@addMeasuredTime
    }

    val workspaceModel = WorkspaceModel.getInstance(targetProject)
    val entitiesCopyAtBuilder = copyEntitiesToEmptyStorage(entityStorage.current, workspaceModel.getVirtualFileUrlManager())
    workspaceModel.updateProjectModel("Sync global entities with project: ${targetProject.name}") { builder ->
      builder.replaceBySource(globalEntitiesFilter, entitiesCopyAtBuilder)
    }
  }

  fun applyStateToProjectBuilder(
    targetBuilder: MutableEntityStorage,
    workspaceModel: WorkspaceModelImpl
  ): Unit = applyStateToProjectBuilderTimeMs.addMeasuredTime {
    LOG.info("Sync global entities with mutable entity storage")
    targetBuilder.replaceBySource(
      sourceFilter = globalEntitiesFilter,
      replaceWith = copyEntitiesToEmptyStorage(entityStorage.current, workspaceModel.getVirtualFileUrlManager()),
    )
  }

  @RequiresWriteLock
  fun syncEntitiesWithProject(sourceProject: Project): Unit = syncEntitiesWithProjectTimeMs.addMeasuredTime {
    ThreadingAssertions.assertWriteAccess()

    filteredProject = sourceProject
    val entitiesCopyAtBuilder = copyEntitiesToEmptyStorage(WorkspaceModel.getInstance(sourceProject).currentSnapshot,
                                                           virtualFileManager)
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
      val libraryEntityCopy = mutableEntityStorage addEntity  LibraryEntity(libraryEntity.name, libraryEntity.tableId, libraryRootsCopy, entitySourceCopy) {
        typeId = libraryEntity.typeId
        excludedRoots = excludedRootsCopy
        libraryProperties = libraryPropertiesCopy
      }
      val libraryBridge = storage.libraryMap.getDataByEntity(libraryEntity)
      if (libraryBridge != null) mutableEntityStorage.mutableLibraryMap.addIfAbsent(libraryEntityCopy, libraryBridge)
    }

    // Copying sdks
    storage.entities(SdkEntity::class.java).forEach { sdkEntity ->
      if (!globalEntitiesFilter.invoke(sdkEntity.entitySource)) return@forEach
      val sdkRootsCopy = sdkEntity.roots.map { root ->
        SdkRoot(root.url.createCopyAtManager(vfuManager), root.type)
      }

      val entitySourceCopy = (sdkEntity.entitySource as JpsGlobalFileEntitySource).copy(vfuManager)
      val sdkEntityCopy = mutableEntityStorage addEntity SdkEntity(sdkEntity.name, sdkEntity.type, sdkRootsCopy, sdkEntity.additionalData, entitySourceCopy) {
        homePath = sdkEntity.homePath?.createCopyAtManager(vfuManager)
        version = sdkEntity.version
      }
      val sdkBridge = storage.sdkMap.getDataByEntity(sdkEntity)
      if (sdkBridge != null) {
        mutableEntityStorage.mutableSdkMap.addIfAbsent(sdkEntityCopy, sdkBridge)
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
    private val LOG = logger<GlobalWorkspaceModel>()

    @RequiresBlockingContext
    @JvmStatic
    fun getInstance(descriptor: EelDescriptor): GlobalWorkspaceModel {
      return ApplicationManager.getApplication().service<GlobalWorkspaceModelRegistry>().getGlobalModel(descriptor)
    }

    suspend fun getInstanceAsync(descriptor: EelDescriptor): GlobalWorkspaceModel {
      return ApplicationManager.getApplication().serviceAsync<GlobalWorkspaceModelRegistry>().getGlobalModel(descriptor)
    }

    suspend fun getInstanceByInternalName(name: GlobalWorkspaceModelCache.InternalEnvironmentName): GlobalWorkspaceModel {
      return ApplicationManager.getApplication().serviceAsync<GlobalWorkspaceModelRegistry>().getGlobalModelByDescriptorName(name)
    }

    fun getInstancesBlocking(): List<GlobalWorkspaceModel> = service<GlobalWorkspaceModelRegistry>().getGlobalModels()

    suspend fun getInstances(): List<GlobalWorkspaceModel> = serviceAsync<GlobalWorkspaceModelRegistry>().getGlobalModels()

    private val updatesCounter: AtomicLong = AtomicLong()
    private val totalUpdatesTimeMs = MillisecondsMeasurer()
    private val applyStateToProjectTimeMs = MillisecondsMeasurer()
    private val applyStateToProjectBuilderTimeMs = MillisecondsMeasurer()
    private val syncEntitiesWithProjectTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val updateTimesCounter = meter.counterBuilder("workspaceModel.global.updates.count").buildObserver()
      val totalUpdatesTimeCounter = meter.counterBuilder("workspaceModel.global.updates.ms").buildObserver()
      val applyStateToProjectTimeCounter = meter.counterBuilder("workspaceModel.global.apply.state.to.project.ms").buildObserver()
      val applyStateToProjectBuilderTimeCounter = meter.counterBuilder("workspaceModel.global.apply.state.to.project.builder.ms").buildObserver()
      val syncEntitiesWithProjectTimeCounter = meter.counterBuilder("workspaceModel.sync.entities.ms").buildObserver()

      meter.batchCallback(
        {
          updateTimesCounter.record(updatesCounter.get())
          totalUpdatesTimeCounter.record(totalUpdatesTimeMs.asMilliseconds())
          applyStateToProjectTimeCounter.record(applyStateToProjectTimeMs.asMilliseconds())
          applyStateToProjectBuilderTimeCounter.record(applyStateToProjectBuilderTimeMs.asMilliseconds())
          syncEntitiesWithProjectTimeCounter.record(syncEntitiesWithProjectTimeMs.asMilliseconds())
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

private fun VirtualFileUrl.createCopyAtManager(manager: VirtualFileUrlManager): VirtualFileUrl = manager.getOrCreateFromUrl(url)

private fun ExcludeUrlEntity.copy(entitySource: EntitySource, manager: VirtualFileUrlManager): ExcludeUrlEntity.Builder {
  return ExcludeUrlEntity(url.createCopyAtManager(manager), entitySource)
}

private fun LibraryPropertiesEntity.copy(entitySource: EntitySource): LibraryPropertiesEntity.Builder {
  val originalPropertiesXmlTag = propertiesXmlTag
  return LibraryPropertiesEntity(entitySource) {
    this.propertiesXmlTag = originalPropertiesXmlTag
  }
}

private fun JpsGlobalFileEntitySource.copy(manager: VirtualFileUrlManager): JpsGlobalFileEntitySource {
  return JpsGlobalFileEntitySource(file.createCopyAtManager(manager))
}

@ApiStatus.Internal
@VisibleForTesting
@Service(Service.Level.APP)
class GlobalWorkspaceModelRegistry {
  companion object {
    const val GLOBAL_WORKSPACE_MODEL_LOCAL_CACHE_ID: String = "Local"
  }

  private val environmentToModel = ConcurrentHashMap<EelDescriptor, GlobalWorkspaceModel>()

  fun getGlobalModel(descriptor: EelDescriptor): GlobalWorkspaceModel {
    val protectedDescriptor = if (Registry.`is`("ide.workspace.model.per.environment.model.separation")) descriptor else LocalEelDescriptor
    val internalName = if (protectedDescriptor is LocalEelDescriptor) {
      GLOBAL_WORKSPACE_MODEL_LOCAL_CACHE_ID
    }
    else {
      EelProvider.EP_NAME.extensionList.firstNotNullOfOrNull { eelProvider ->
        eelProvider.getInternalName(protectedDescriptor)
      }
      ?: throw IllegalArgumentException("Descriptor $protectedDescriptor must be registered before using in Workspace Model")
    }
    return environmentToModel.computeIfAbsent(protectedDescriptor) { GlobalWorkspaceModel(protectedDescriptor, InternalEnvironmentNameImpl(internalName)) }
  }

  fun getGlobalModelByDescriptorName(name: GlobalWorkspaceModelCache.InternalEnvironmentName): GlobalWorkspaceModel {
    val protectedName = if (Registry.`is`("ide.workspace.model.per.environment.model.separation")) name.name else GLOBAL_WORKSPACE_MODEL_LOCAL_CACHE_ID
    val descriptor = if (protectedName == GLOBAL_WORKSPACE_MODEL_LOCAL_CACHE_ID) {
      LocalEelDescriptor
    }
    else {
      EelProvider.EP_NAME.extensionList.firstNotNullOf { eelProvider -> eelProvider.getEelDescriptorByInternalName(protectedName) }
    }
    val model = getGlobalModel(descriptor)
    return model
  }

  fun getGlobalModels(): List<GlobalWorkspaceModel> {
    return if (Registry.`is`("ide.workspace.model.per.environment.model.separation")) {
      environmentToModel.values.toList()
    }
    else {
      listOf(getGlobalModel(LocalEelDescriptor))
    }
  }

  @TestOnly
  fun dropCaches() {
    environmentToModel.clear()
  }

}

@ApiStatus.Internal
class InternalEnvironmentNameImpl(override val name: String) : GlobalWorkspaceModelCache.InternalEnvironmentName
