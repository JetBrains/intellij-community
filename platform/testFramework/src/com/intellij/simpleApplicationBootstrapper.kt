// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.IconManager
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.EventQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
  Main.setFlags(arrayOf("inspect", "", "", ""))
  assert(Main.isHeadless())
  assert(Main.isCommandLine())
  PluginManagerCore.isUnitTestMode = true
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)

  val loadedPluginFuture = CompletableFuture.supplyAsync(Supplier {
    PluginManagerCore.getLoadedPlugins(PathManager::class.java.classLoader)
  }, AppExecutorUtil.getAppExecutorService())

  setupEventQueue()

  val app = ApplicationImpl(true, true, true, true)

  if (SystemProperties.getBooleanProperty("tests.assertOnMissedCache", true)) {
    RecursionManager.assertOnMissedCache(app)
  }

  IconManager.activate()
  val plugins: List<IdeaPluginDescriptorImpl>
  try {
    plugins = registerRegistryAndInitStore(registerAppComponents(loadedPluginFuture, app), app)
      .get(20, TimeUnit.SECONDS)

    val boundedExecutor = createExecutorToPreloadServices()

    Registry.getInstance().markAsLoaded()
    val preloadServiceFuture = preloadServices(plugins, app, activityPrefix = "", executor = boundedExecutor)
    app.loadComponents(null)

    preloadServiceFuture
      .thenCompose { callAppInitialized(app, boundedExecutor) }
      .get(20, TimeUnit.SECONDS)
  }
  catch (e: TimeoutException) {
    throw RuntimeException("Cannot preload services in 20 seconds: ${ThreadDumper.dumpThreadsToString()}", e)
  }
  catch (e: ExecutionException) {
    throw e.cause ?: e
  }
  catch (e: InterruptedException) {
    throw e.cause ?: e
  }
}
