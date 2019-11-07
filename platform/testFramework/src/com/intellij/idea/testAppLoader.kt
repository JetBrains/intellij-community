// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.IconManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

@ApiStatus.Internal
fun loadTestApp() {
  val args = arrayOf("inspect", "", "", "")
  Main.setFlags(args)
  assert(Main.isHeadless())
  assert(Main.isCommandLine())
  PluginManagerCore.isUnitTestMode = true
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)

  val loadedPluginFuture = CompletableFuture.supplyAsync(Supplier {
    PluginManagerCore.getLoadedPlugins(IdeaTestApplication::class.java.classLoader)
  }, AppExecutorUtil.getAppExecutorService())
  StartupUtil.replaceSystemEventQueue(logger<IdeaTestApplication>())
  val app = ApplicationImpl(true, true, true, true)
  IconManager.activate()
  val plugins: List<IdeaPluginDescriptorImpl>
  try {
    plugins = registerRegistryAndInitStore(registerAppComponents(loadedPluginFuture, app), app)
      .get(20, TimeUnit.SECONDS)

    val boundedExecutor = createExecutorToPreloadServices()
    val preloadServiceFuture = preloadServices(plugins, app, boundedExecutor, "")
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