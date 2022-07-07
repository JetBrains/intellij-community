// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.callAppInitialized
import com.intellij.idea.initConfigurationStore
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyBean.Companion.addKeysFromPlugins
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.util.SystemProperties
import kotlinx.coroutines.*
import java.time.Duration

@OptIn(DelicateCoroutinesApi::class)
internal fun loadAppInUnitTestMode(isHeadless: Boolean) {
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
            asyncScope = GlobalScope + CoroutineExceptionHandler { _, throwable -> logger<TestApplicationManager>().error(throwable) }
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