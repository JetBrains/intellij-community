// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.*
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridge
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.*
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicLong
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
class JpsGlobalModelSynchronizerImpl(private val coroutineScope: CoroutineScope) : JpsGlobalModelSynchronizer {
  private var loadedFromDisk: Boolean = false
  private val isLibSerializationProhibited: Boolean
    get() = !forceEnableLoading && ApplicationManager.getApplication().isUnitTestMode

  override fun loadInitialState(mutableStorage: MutableEntityStorage, initialEntityStorage: VersionedEntityStorage,
                                loadedFromCache: Boolean): () -> Unit = jpsLoadInitialStateMs.addMeasuredTimeMillis {
    val callback = if (loadedFromCache) {
      val callback = bridgesInitializationCallback(mutableStorage, initialEntityStorage)
      coroutineScope.launch {
        delay(10.seconds)
        delayLoadGlobalWorkspaceModel()
      }
      callback
    }
    else {
      loadGlobalEntitiesToEmptyStorage(mutableStorage, initialEntityStorage, initializeBridges = true)
    }

    return@addMeasuredTimeMillis callback
  }

  suspend fun saveGlobalEntities() = jpsSaveGlobalEntitiesMs.addMeasuredTimeMillis {
    val serializers = createSerializers()
    val contentWriter = (ApplicationManager.getApplication().stateStore as ApplicationStoreJpsContentReader).createContentWriter()
    val entityStorage = GlobalWorkspaceModel.getInstance().entityStorage.current
    serializers.forEach { serializer ->
      val entities = entityStorage.entities(serializer.mainEntityClass).toList()
      LOG.info("Saving global entities ${serializer.mainEntityClass.name} to files")
      if (serializer.mainEntityClass == SdkEntity::class.java) {
        assertUnexpectedAdditionalDataModification(entityStorage)
      }
      serializer.saveEntities(entities, emptyMap(), entityStorage, contentWriter)
    }
    contentWriter.saveSession()
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

  private suspend fun delayLoadGlobalWorkspaceModel() {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    if (globalWorkspaceModel.loadedFromCache && !loadedFromDisk) {
      val mutableStorage = MutableEntityStorage.create()

      // We don't need to initialize bridges one more time at delay loading. Otherwise, we will get the new instance of bridge in the mappings
      loadGlobalEntitiesToEmptyStorage(mutableStorage, globalWorkspaceModel.entityStorage, initializeBridges = false)
      writeAction {
        globalWorkspaceModel.updateModel("Sync global entities with state") { builder ->
          builder.replaceBySource({ it is JpsGlobalFileEntitySource }, mutableStorage)
        }
      }
    }
  }

  private fun loadGlobalEntitiesToEmptyStorage(mutableStorage: MutableEntityStorage,
                                               initialEntityStorage: VersionedEntityStorage,
                                               initializeBridges: Boolean): () -> Unit {
    val contentReader = (ApplicationManager.getApplication().stateStore as ApplicationStoreJpsContentReader).createContentReader()
    val serializers = createSerializers()
    val errorReporter = object : ErrorReporter {
      override fun reportError(message: String, file: VirtualFileUrl) {
        LOG.warn(message)
      }
    }
    serializers.forEach { serializer ->
      LOG.info("Loading global entities ${serializer.mainEntityClass.name} from files")
      val newEntities = serializer.loadEntities(contentReader, errorReporter, VirtualFileUrlManager.getGlobalInstance())
      serializer.checkAndAddToBuilder(mutableStorage, mutableStorage, newEntities.data)
      newEntities.exception?.let { throw it }
    }
    val callback = if (initializeBridges) {
      bridgesInitializationCallback(mutableStorage, initialEntityStorage)
    } else {
      { }
    }
    loadedFromDisk = true
    return callback
  }

  private fun createSerializers(): List<JpsFileEntitiesSerializer<WorkspaceEntity>> {
    val sortedRootTypes = OrderRootType.getSortedRootTypes().mapNotNull { it.sdkRootName }
    return JpsGlobalEntitiesSerializers.createApplicationSerializers(VirtualFileUrlManager.getGlobalInstance(),
                                                                     sortedRootTypes,
                                                                     !isLibSerializationProhibited)
  }

  private fun bridgesInitializationCallback(mutableStorage: MutableEntityStorage,
                                            initialEntityStorage: VersionedEntityStorage): () -> Unit {
    val sdkCallback = GlobalSdkTableBridge.getInstance().initializeSdkBridgesAfterLoading(mutableStorage,
                                                                                          initialEntityStorage)
    val librariesCallback = GlobalLibraryTableBridge.getInstance().initializeLibraryBridgesAfterLoading(mutableStorage,
                                                                                                        initialEntityStorage)
    return {
      sdkCallback.invoke()
      librariesCallback.invoke()
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

    private val jpsLoadInitialStateMs: AtomicLong = AtomicLong()
    private val jpsSaveGlobalEntitiesMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val jpsLoadInitialStateCounter = meter.counterBuilder("jps.load.initial.state.ms").buildObserver()
      val jpsSaveGlobalEntitiesCounter = meter.counterBuilder("jps.save.global.entities.ms").buildObserver()

      meter.batchCallback(
        {
          jpsLoadInitialStateCounter.record(jpsLoadInitialStateMs.get())
          jpsSaveGlobalEntitiesCounter.record(jpsSaveGlobalEntitiesMs.get())
        },
        jpsLoadInitialStateCounter, jpsSaveGlobalEntitiesCounter
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}