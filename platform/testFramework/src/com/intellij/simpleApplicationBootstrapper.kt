// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.Main
import com.intellij.idea.callAppInitialized
import com.intellij.idea.initConfigurationStore
import com.intellij.idea.preloadServices
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyBean
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.util.SystemProperties
import java.awt.EventQueue
import java.util.concurrent.*
import java.util.function.Supplier

fun loadHeadlessAppInUnitTestMode() {
  doLoadApp {
    EventQueue.invokeAndWait {
      // replaces system event queue
      IdeEventQueue.getInstance()
    }
  }
}

internal fun doLoadApp(setupEventQueue: () -> Unit) {
  var isHeadless = true
  if (System.getProperty("java.awt.headless") == "false") {
    isHeadless = false
  }
  else {
    System.setProperty("java.awt.headless", "true")
  }
  Main.setHeadlessInTestMode(isHeadless)
  PluginManagerCore.isUnitTestMode = true
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)

  val loadedPluginFuture = CompletableFuture.supplyAsync(Supplier {
    PluginManagerCore.getLoadedPlugins(PathManager::class.java.classLoader)
  }, ForkJoinPool.commonPool())

  setupEventQueue()

  val app = ApplicationImpl(true, true, isHeadless, true)

  if (SystemProperties.getBooleanProperty("tests.assertOnMissedCache", true)) {
    RecursionManager.assertOnMissedCache(app)
  }

  val plugins: List<IdeaPluginDescriptorImpl>
  try {
    // 40 seconds - tests maybe executed on cloud agents where IO speed is a very slow
    plugins = loadedPluginFuture.get(40, TimeUnit.SECONDS)
    app.registerComponents(plugins, app, null, null)
    initConfigurationStore(app)
    RegistryKeyBean.addKeysFromPlugins()
    Registry.markAsLoaded()
    val preloadServiceFuture = preloadServices(plugins, app, activityPrefix = "")
    app.loadComponents(null)

    preloadServiceFuture.get(40, TimeUnit.SECONDS)
    ForkJoinTask.invokeAll(callAppInitialized(app))

    (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()
  }
  catch (e: TimeoutException) {
    throw RuntimeException("Cannot preload services in 40 seconds: ${ThreadDumper.dumpThreadsToString()}", e)
  }
  catch (e: ExecutionException) {
    throw e.cause ?: e
  }
  catch (e: InterruptedException) {
    throw e.cause ?: e
  }
}
