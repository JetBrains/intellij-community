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
    if (!processPerProjectSupport().isEnabled()) {
      return@coroutineScope
    }

    val path = PathManager.getOriginalConfigDir()
    LOG.info("P3 mode is enabled, configuration files with be synchronized with $path.")
    val app = ApplicationManager.getApplication()
    val compoundStreamProvider = app.stateStore.storageManager.streamProvider
    val streamProvider = compoundStreamProvider.getInstanceOf(SharedConfigFolderStreamProvider::class.java) as SharedConfigFolderStreamProvider
    val configFilesUpdatedByThisProcess = streamProvider.configFilesUpdatedByThisProcess
    launch {
      SharedConfigFolderUtil.installFsWatcher(path, configFilesUpdatedByThisProcess)
    }

    launch {
      while (isActive) {
        delay(1.minutes)
        configFilesUpdatedByThisProcess.cleanUpOldData()
      }
    }

    app.messageBus.connect(this@coroutineScope).subscribe(DynamicPluginListener.TOPIC, serviceAsync<P3DynamicPluginSynchronizer>())
    coroutineScope {
      setupSyncDisabledPlugins(path, this)
    }
  }

  private fun setupSyncDisabledPlugins(path: Path, asyncScope: CoroutineScope) {
    val syncDisabledPluginsRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    asyncScope.launch(Dispatchers.IO) {
      syncDisabledPluginsRequests.debounce(100).collectLatest {
        syncDisabledPluginsFile(path)
      }
    }
    DisabledPluginsState.addDisablePluginListener {
      syncDisabledPluginsRequests.tryEmit(Unit)
    }
  }

  private fun syncDisabledPluginsFile(originalConfigDir: Path) {
    val sourceFile = PathManager.getConfigDir().resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)
    val targetFileName = processPerProjectSupport().disabledPluginsFileName
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
