// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.*
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMs
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.serialization.impl.*
import com.intellij.platform.workspace.storage.*
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.legacyBridge.sdk.GlobalSdkTableBridge
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis
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
                                loadedFromCache: Boolean): () -> Unit {
    val start = System.currentTimeMillis()

    val callback = if (loadedFromCache) {
      val callback = bridgesInitializationCallback(mutableStorage, initialEntityStorage)
      coroutineScope.launch {
        delay(10.seconds)
        delayLoadGlobalWorkspaceModel()
      }
      callback
    }
    else {
      loadGlobalEntitiesToEmptyStorage(mutableStorage, initialEntityStorage)
    }

    jpsLoadInitialStateMs.addElapsedTimeMs(start)
    return callback
  }

  fun saveGlobalEntities(writer: JpsFileContentWriter) {
    jpsSaveGlobalEntitiesMs.addAndGet(
      measureTimeMillis {
        val serializers = createSerializers()
        val entityStorage = GlobalWorkspaceModel.getInstance().entityStorage.current
        serializers.forEach { serializer ->
          val entities = entityStorage.entities(serializer.mainEntityClass).toList()
          LOG.info("Saving global entities ${serializer.mainEntityClass.name} to files")
          serializer.saveEntities(entities, emptyMap(), entityStorage, writer)
        }
      })
  }

  private suspend fun delayLoadGlobalWorkspaceModel() {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    if (globalWorkspaceModel.loadedFromCache && !loadedFromDisk) {
      val mutableStorage = MutableEntityStorage.create()

      loadGlobalEntitiesToEmptyStorage(mutableStorage, globalWorkspaceModel.entityStorage)
      writeAction {
        globalWorkspaceModel.updateModel("Sync global entities with state") { builder ->
          builder.replaceBySource({ it is JpsGlobalFileEntitySource }, mutableStorage)
        }
      }
    }
  }

  private fun loadGlobalEntitiesToEmptyStorage(mutableStorage: MutableEntityStorage,
                                               initialEntityStorage: VersionedEntityStorage): () -> Unit {
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
    val callback = bridgesInitializationCallback(mutableStorage, initialEntityStorage)
    loadedFromDisk = true
    return callback
  }

  private fun createSerializers(): List<JpsFileEntitiesSerializer<WorkspaceEntity>> {
    return JpsGlobalEntitiesSerializers.createApplicationSerializers(VirtualFileUrlManager.getGlobalInstance(),
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
      val jpsLoadInitialStateGauge = meter.gaugeBuilder("jps.load.initial.state.ms")
        .ofLongs().setDescription("Total time spent in loadInitialState").buildObserver()

      val jpsSaveGlobalEntitiesGauge = meter.gaugeBuilder("jps.save.global.entities.ms")
        .ofLongs().setDescription("Total time spent on jps saving global entities").buildObserver()

      meter.batchCallback(
        {
          jpsLoadInitialStateGauge.record(jpsLoadInitialStateMs.get())
          jpsSaveGlobalEntitiesGauge.record(jpsSaveGlobalEntitiesMs.get())
        },
        jpsLoadInitialStateGauge, jpsSaveGlobalEntitiesGauge
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}