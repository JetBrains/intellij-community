// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.shared

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.ApplicationLoadListener
import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.impl.processPerProjectSupport
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.time.Duration.Companion.minutes

private class P3SharedConfigFolderApplicationLoadListener : ApplicationLoadListener {
  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path) {
    if (application.isUnitTestMode || !processPerProjectSupport().isEnabled()) {
      return
    }

    SharedConfigFolderUtil.installStreamProvider(application, PathManager.getOriginalConfigDir())
  }
}

@OptIn(FlowPreview::class)
private class ProcessPerProjectSharedConfigFolderApplicationInitializedListener : ApplicationActivity {
  override suspend fun execute() = coroutineScope {
  val path = PathManager.getOriginalConfigDir()
    if (processPerProjectSupport().isEnabled()) {
      LOG.info("P3 mode is enabled, configuration files with be synchronized with $path.")
      val application = ApplicationManager.getApplication()
      val compoundStreamProvider = application.stateStore.storageManager.streamProvider
      val streamProvider = compoundStreamProvider.getInstanceOf(SharedConfigFolderStreamProvider::class.java) as SharedConfigFolderStreamProvider
      val configFilesUpdatedByThisProcess = streamProvider.configFilesUpdatedByThisProcess
      SharedConfigFolderUtil.installFsWatcher(path, configFilesUpdatedByThisProcess)

      launch {
        while (isActive) {
          delay(1.minutes)
          configFilesUpdatedByThisProcess.cleanUpOldData()
        }
      }
      
      application.messageBus.connect().subscribe(DynamicPluginListener.TOPIC, serviceAsync<P3DynamicPluginSynchronizer>())
      coroutineScope {
        setupSyncEarlyAccessRegistry(path, this)
        setupSyncDisabledPlugins(path, this)
      }
    }
  }

  private fun setupSyncDisabledPlugins(path: Path, asyncScope: CoroutineScope) {
    val syncDisabledPluginsRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    asyncScope.launch(Dispatchers.IO) {
      syncDisabledPluginsRequests.debounce(100).collectLatest {
        syncCustomConfigFile(path, DisabledPluginsState.DISABLED_PLUGINS_FILENAME)
      }
    }
    DisabledPluginsState.addDisablePluginListener {
      syncDisabledPluginsRequests.tryEmit(Unit)
    }
  }

  private suspend fun setupSyncEarlyAccessRegistry(path: Path, asyncScope: CoroutineScope) {
    withContext(Dispatchers.IO) {
      syncCustomConfigFile(path, EarlyAccessRegistryManager.fileName)
    }
    val saveEarlyAccessRegistryRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    asyncScope.launch(Dispatchers.IO) {
      saveEarlyAccessRegistryRequests.debounce(100).collectLatest {
        EarlyAccessRegistryManager.syncAndFlush()
        syncCustomConfigFile(path, EarlyAccessRegistryManager.fileName)
      }
    }
    ApplicationManager.getApplication().messageBus.connect().subscribe(RegistryManager.TOPIC, object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        if (value.key in EarlyAccessRegistryManager.getOrLoadMap()) {
          saveEarlyAccessRegistryRequests.tryEmit(Unit)
        }
      }
    })
  }

  private fun syncCustomConfigFile(originalConfigDir: Path, fileName: String) {
    val sourceFile = PathManager.getConfigDir().resolve(fileName)
    val targetFileName = fileName.takeIf { it != DisabledPluginsState.DISABLED_PLUGINS_FILENAME }
                         ?: processPerProjectSupport().disabledPluginsFileName
    val targetFile = originalConfigDir.resolve(targetFileName)
    if (sourceFile.exists()) {
      SharedConfigFolderUtil.writeToSharedFile(targetFile, sourceFile.readBytes())
    }
    else {
      SharedConfigFolderUtil.deleteSharedFile(targetFile)
    }
  }
}

private val LOG = logger<ProcessPerProjectSharedConfigFolderApplicationInitializedListener>()
