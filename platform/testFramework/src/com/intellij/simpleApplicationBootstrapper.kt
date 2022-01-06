// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.idea.Main
import com.intellij.idea.callAppInitialized
import com.intellij.idea.initConfigurationStore
import com.intellij.idea.preloadServices
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyBean
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.testFramework.UITestUtil
import com.intellij.util.SystemProperties
import java.awt.EventQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
    UITestUtil.setHeadlessProperty(true)
  }
  Main.setHeadlessInTestMode(isHeadless)
  PluginManagerCore.isUnitTestMode = true
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)

  PluginManagerCore.scheduleDescriptorLoading()
  val loadedModuleFuture = PluginManagerCore.getInitPluginFuture()

  setupEventQueue()

  val app = ApplicationImpl(true, true, isHeadless, true)

  if (SystemProperties.getBooleanProperty("tests.assertOnMissedCache", true)) {
    RecursionManager.assertOnMissedCache(app)
  }

  val pluginSet: PluginSet
  try {
    // 40 seconds - tests maybe executed on cloud agents where IO speed is a very slow
    pluginSet = loadedModuleFuture.get(40, TimeUnit.SECONDS)
    app.registerComponents(modules = pluginSet.getEnabledModules(), app = app, precomputedExtensionModel = null, listenerCallbacks = null)
    initConfigurationStore(app)
    RegistryKeyBean.addKeysFromPlugins()
    Registry.markAsLoaded()
    val preloadServiceFuture = preloadServices(pluginSet.getEnabledModules(), app, activityPrefix = "")
    app.loadComponents()

    preloadServiceFuture.get(40, TimeUnit.SECONDS)
    ForkJoinTask.invokeAll(callAppInitialized(app))
    StartUpMeasurer.setCurrentState(LoadingState.APP_STARTED)

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
