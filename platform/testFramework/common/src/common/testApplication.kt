// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.Main
import com.intellij.idea.callAppInitialized
import com.intellij.idea.initConfigurationStore
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyBean.Companion.addKeysFromPlugins
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.psi.impl.DocumentCommitProcessor
import com.intellij.psi.impl.DocumentCommitThread
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.UITestUtil
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.time.Duration
import java.util.concurrent.TimeUnit

private val LOG: Logger = Logger.getInstance("com.intellij.testFramework.common.TestApplicationKt")

@TestOnly
@Internal
fun loadApp() {
  loadApp(UITestUtil::setupEventQueue)
}

@TestOnly
@OptIn(DelicateCoroutinesApi::class)
@Internal
fun loadApp(setupEventQueue: Runnable) {
  val isHeadless = UITestUtil.getAndSetHeadlessProperty()
  Main.setHeadlessInTestMode(isHeadless)
  PluginManagerCore.isUnitTestMode = true
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)
  PluginManagerCore.scheduleDescriptorLoading(GlobalScope)
  setupEventQueue.run()
  loadAppInUnitTestMode(isHeadless)
}

@TestOnly
@OptIn(DelicateCoroutinesApi::class)
private fun loadAppInUnitTestMode(isHeadless: Boolean) {
  val loadedModuleFuture = PluginManagerCore.getInitPluginFuture()

  val app = ApplicationImpl(true, true, isHeadless, true)

  if (SystemProperties.getBooleanProperty("tests.assertOnMissedCache", true)) {
    RecursionManager.assertOnMissedCache(app)
  }

  try {
    runBlocking {
      // 40 seconds - tests maybe executed on cloud agents where I/O is very slow
      val pluginSet = withTimeout(Duration.ofSeconds(40).toMillis()) {
        loadedModuleFuture.await()
      }

      app.registerComponents(pluginSet.getEnabledModules(), app, null, null)
      initConfigurationStore(app)
      addKeysFromPlugins()
      Registry.markAsLoaded()

      coroutineScope {
        withTimeout(Duration.ofSeconds(40).toMillis()) {
          app.preloadServices(
            modules = pluginSet.getEnabledModules(),
            activityPrefix = "",
            syncScope = this,
            asyncScope = GlobalScope + CoroutineExceptionHandler { _, throwable -> LOG.error(throwable) }
          )
        }
        app.loadComponents()
      }

      callAppInitialized(app)
    }

    StartUpMeasurer.setCurrentState(LoadingState.APP_STARTED)
    (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()
  }
  catch (e: InterruptedException) {
    throw e.cause ?: e
  }
}

@TestOnly
@Internal
fun assertNonDefaultProjectsAreNotLeaked() {
  try {
    LeakHunter.checkNonDefaultProjectLeak()
  }
  catch (e: AssertionError) {
    publishHeapDump("leakedProjects")
    throw e
  }
  catch (e: Exception) {
    publishHeapDump("leakedProjects")
    throw e
  }
}

@TestOnly
fun waitForAppLeakingThreads(application: Application, timeout: Long, timeUnit: TimeUnit) {
  require(!application.isDisposed)

  val index = application.getServiceIfCreated(FileBasedIndex::class.java) as? FileBasedIndexImpl
  index?.changedFilesCollector?.waitForVfsEventsExecuted(timeout, timeUnit)

  val commitThread = application.getServiceIfCreated(DocumentCommitProcessor::class.java) as? DocumentCommitThread
  commitThread?.waitForAllCommits(timeout, timeUnit)
}
