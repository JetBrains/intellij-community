// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.JpsGlobalModelLoadedListener
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.GlobalEntityBridgeAndEventHandler
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

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
 * [x] Rework delayed synchronize
 * [x] Check sync with Maven and External system
 */

/**
 * The logic here is similar to [com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleBridgeLoaderService]
 * but for global level entities. This class orchestrates the global entities/bridges loading, it's responsible for:
 * 1) Reading .xml files with configs if not loaded from cache.
 * 2) Call initialization of bridges after cache loading
 * 3) Reading .xml on delayed sync
 */
@ApiStatus.Internal
class JpsGlobalModelSynchronizerImpl(private val coroutineScope: CoroutineScope) : JpsGlobalModelSynchronizer {
  private var loadedFromDisk: Boolean = false
  private val isSerializationProhibited: Boolean
    get() = !forceEnableLoading && ApplicationManager.getApplication().isUnitTestMode
  private lateinit var virtualFileUrlManager: VirtualFileUrlManager

  override fun loadInitialState(
    environmentName: GlobalWorkspaceModelCache.InternalEnvironmentName, mutableStorage: MutableEntityStorage, initialEntityStorage: VersionedEntityStorage,
    loadedFromCache: Boolean,
  ): () -> Unit = jpsLoadInitialStateMs.addMeasuredTime {
    val callback = if (loadedFromCache) {
      val callback = bridgesInitializationCallback(environmentName, mutableStorage, initialEntityStorage, false)
      coroutineScope.launch {
        delay(5.seconds)
        delayLoadGlobalWorkspaceModel(environmentName)
      }
      callback
    }
    else {
      loadGlobalEntitiesToEmptyStorage(environmentName, mutableStorage, initialEntityStorage, initializeBridges = true)
    }

    return@addMeasuredTime callback
  }

  override fun setVirtualFileUrlManager(vfuManager: VirtualFileUrlManager) {
    virtualFileUrlManager = vfuManager
  }

  suspend fun saveGlobalEntities() = jpsSaveGlobalEntitiesMs.addMeasuredTime {
    val globalWorkspaceModels = GlobalWorkspaceModel.getInstances()
    globalWorkspaceModels.forEach { globalWorkspaceModel ->
      setVirtualFileUrlManager(globalWorkspaceModel.getVirtualFileUrlManager())
      val entityStorage = globalWorkspaceModel.entityStorage.current
      val serializers = createSerializers()
      val contentWriter = (ApplicationManager.getApplication().stateStore as ApplicationStoreJpsContentReader).createContentWriter()
      serializers.forEach { serializer ->
        serializeEntities(entityStorage, serializer, contentWriter)
      }
      contentWriter.saveSession()
    }
  }

  @TestOnly
  suspend fun saveSdkEntities() {
    val sortedRootTypes = OrderRootType.getSortedRootTypes().mapNotNull { it.sdkRootName }
    val sdkSerializer = JpsGlobalEntitiesSerializers.createSdkSerializer(virtualFileUrlManager, sortedRootTypes, Path(PathManager.getOptionsPath())) as JpsFileEntityTypeSerializer<WorkspaceEntity>
    val contentWriter = (ApplicationManager.getApplication().stateStore as ApplicationStoreJpsContentReader).createContentWriter()
    GlobalWorkspaceModel.getInstances().forEach {
      val entityStorage = it.entityStorage.current
      serializeEntities(entityStorage, sdkSerializer, contentWriter)
      contentWriter.saveSession()
    }
  }

  private fun serializeEntities(entityStorage: EntityStorage, serializer: JpsFileEntityTypeSerializer<WorkspaceEntity>,
                                contentWriter: JpsAppFileContentWriter) {
    val entities = entityStorage.entities(serializer.mainEntityClass).toList()
    LOG.info("Saving global entities ${serializer.mainEntityClass.name} to files")

    val filteredEntities = if (serializer.mainEntityClass == LibraryEntity::class.java) {
      // We need to filter custom libraries, they will be serialized by the client code and not by the platform
      entities.filter { it.entitySource is JpsGlobalFileEntitySource }
    } else entities

    if (serializer.mainEntityClass == SdkEntity::class.java) {
      assertUnexpectedAdditionalDataModification(entityStorage)
    }

    if (filteredEntities.isEmpty()) {
      // Remove empty files
      serializer.deleteObsoleteFile(serializer.fileUrl.url, contentWriter)
    } else {
      serializer.saveEntities(filteredEntities, emptyMap(), entityStorage, contentWriter)
    }
  }


  private fun assertUnexpectedAdditionalDataModification(entityStorage: EntityStorage) {
    entityStorage.entities(SdkEntity::class.java).forEach { sdkEntity ->
      val projectJdkImpl = entityStorage.sdkMap.getDataByEntity(sdkEntity) ?: error(
        "SdkBridge has to be available for the SdkEntity: ${sdkEntity.name}; type: ${sdkEntity.type}; path: ${sdkEntity.homePath}")
      val additionalData = projectJdkImpl.sdkAdditionalData
      if (additionalData == null) return@forEach
      val additionalDataElement = Element(ELEMENT_ADDITIONAL)
      projectJdkImpl.sdkType.saveAdditionalData(additionalData, additionalDataElement)
      val additionalDataAsString = JDOMUtil.write(additionalDataElement)
      if (additionalDataAsString != sdkEntity.additionalData) {
        val className = additionalData.javaClass.name
        LOG.error("$className mismatch for SDK: ${sdkEntity.name}; type: ${sdkEntity.type}; path: ${sdkEntity.homePath};\n" +
                  "$className in entity: \n" +
                  "${sdkEntity.additionalData}\n" +
                  "$className in bridge: \n" +
                  "${additionalDataAsString}\n" +
                  "Probably inconsistent update of the $className, see the documentation of `SdkAdditionalData#markAsCommited` for more information")
      }
    }
  }

  private suspend fun delayLoadGlobalWorkspaceModel(environmentName: GlobalWorkspaceModelCache.InternalEnvironmentName) {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstanceByInternalName(environmentName)
    if (globalWorkspaceModel.loadedFromCache && !loadedFromDisk) {
      val mutableStorage = MutableEntityStorage.create()

      // We don't need to initialize bridges one more time at delay loading. Otherwise, we will get the new instance of bridge in the mappings
      loadGlobalEntitiesToEmptyStorage(environmentName, mutableStorage, globalWorkspaceModel.entityStorage, initializeBridges = false)
      backgroundWriteAction {
        globalWorkspaceModel.updateModel("Sync global entities with state") { builder ->
          builder.replaceBySource({ it is JpsGlobalFileEntitySource }, mutableStorage)
        }
      }
      // Notify the listeners that synchronization process completed
      ApplicationManager.getApplication().messageBus.syncPublisher(JpsGlobalModelLoadedListener.LOADED).loaded()
    }
  }

  private fun loadGlobalEntitiesToEmptyStorage(
    environmentName: GlobalWorkspaceModelCache.InternalEnvironmentName,
    mutableStorage: MutableEntityStorage,
    initialEntityStorage: VersionedEntityStorage,
    initializeBridges: Boolean,
  ): () -> Unit {
    val contentReader = (ApplicationManager.getApplication().stateStore as ApplicationStoreJpsContentReader).createContentReader()
    val serializers = createSerializers()
    val errorReporter = object : ErrorReporter {
      override fun reportError(message: String, file: VirtualFileUrl) {
        LOG.warn(message)
      }
    }
    serializers.forEach { serializer ->
      LOG.info("Loading global entities ${serializer.mainEntityClass.name} from files")
      val newEntities = serializer.loadEntities(contentReader, errorReporter, virtualFileUrlManager)
      serializer.checkAndAddToBuilder(mutableStorage, mutableStorage, newEntities.data)
      newEntities.exception?.let { throw it }
    }
    val callback = if (initializeBridges) {
      bridgesInitializationCallback(environmentName, mutableStorage, initialEntityStorage, true)
    } else {
      { }
    }
    loadedFromDisk = true
    return callback
  }

  private fun createSerializers(): List<JpsFileEntityTypeSerializer<WorkspaceEntity>> {
    if (isSerializationProhibited) return emptyList()
    val sortedRootTypes = OrderRootType.getSortedRootTypes().mapNotNull { it.sdkRootName }
    return JpsGlobalEntitiesSerializers.createApplicationSerializers(virtualFileUrlManager, sortedRootTypes, Path(PathManager.getOptionsPath()))
  }

  private fun bridgesInitializationCallback(
    environmentName: GlobalWorkspaceModelCache.InternalEnvironmentName,
    mutableStorage: MutableEntityStorage,
    initialEntityStorage: VersionedEntityStorage,
    notifyListeners: Boolean,
  ): () -> Unit {
    val descriptor = EelNioBridgeService.getInstanceSync().tryGetDescriptorByName(environmentName.name) ?: LocalEelDescriptor
    val callbacks = GlobalEntityBridgeAndEventHandler.getAllGlobalEntityHandlers(descriptor)
      .map { it.initializeBridgesAfterLoading(mutableStorage, initialEntityStorage) }
    return {
      callbacks.forEach { it.invoke() }
      if (notifyListeners) {
        // Notify the listeners that synchronization process completed
        ApplicationManager.getApplication().messageBus.syncPublisher(JpsGlobalModelLoadedListener.LOADED).loaded()
      }
    }
  }

  companion object {
    private val LOG = logger<JpsGlobalModelSynchronizerImpl>()
    private var forceEnableLoading = false

    @TestOnly
    fun runWithGlobalEntitiesLoadingEnabled(action: () -> Unit) {
      forceEnableLoading = true
      try {
        action()
      }
      finally {
        forceEnableLoading = false
      }
    }

    private val jpsLoadInitialStateMs = MillisecondsMeasurer()
    private val jpsSaveGlobalEntitiesMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val jpsLoadInitialStateCounter = meter.counterBuilder("jps.load.initial.state.ms").buildObserver()
      val jpsSaveGlobalEntitiesCounter = meter.counterBuilder("jps.save.global.entities.ms").buildObserver()

      meter.batchCallback(
        {
          jpsLoadInitialStateCounter.record(jpsLoadInitialStateMs.asMilliseconds())
          jpsSaveGlobalEntitiesCounter.record(jpsSaveGlobalEntitiesMs.asMilliseconds())
        },
        jpsLoadInitialStateCounter, jpsSaveGlobalEntitiesCounter
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}