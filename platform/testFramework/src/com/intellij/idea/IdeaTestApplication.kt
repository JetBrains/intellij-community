// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.ui.IconManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import sun.awt.AWTAutoShutdown
import java.awt.EventQueue
import java.awt.Toolkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import javax.swing.SwingUtilities

class IdeaTestApplication private constructor() {
  companion object {
    @Volatile
    private var ourInstance: IdeaTestApplication? = null
    @Volatile
    private var bootstrapError: RuntimeException? = null
    private val isBootstrappingAppNow = AtomicBoolean()

    private val dataManager: HeadlessDataManager
      get() = ApplicationManager.getApplication().getComponent(DataManager::class.java) as HeadlessDataManager

    @JvmStatic
    fun getInstance(): IdeaTestApplication {
      var result = ourInstance
      if (result == null) {
        try {
          result = createInstance()
        }
        catch (e: RuntimeException) {
          bootstrapError = e
          isBootstrappingAppNow.set(false)
          throw e
        }
      }
      return result
    }

    @Synchronized
    private fun createInstance(): IdeaTestApplication {
      var result = ourInstance
      if (result != null) {
        return result
      }

      bootstrapError?.let {
        throw it
      }

      if (!isBootstrappingAppNow.compareAndSet(false, true)) {
        throw IllegalStateException("App bootstrap is already in process")
      }

      HeavyPlatformTestCase.doAutodetectPlatformPrefix()
      loadTestApp()
      isBootstrappingAppNow.set(false)
      result = IdeaTestApplication()
      ourInstance = result
      return result
    }

    @Synchronized
    @JvmStatic
    private fun disposeInstance() {
      if (ourInstance == null) {
        return
      }
      val app = ApplicationManager.getApplication() as ApplicationImpl
      // `ApplicationManager#ourApplication` will be automatically set to `null`
      app.disposeContainer()
      ourInstance = null
    }
  }

  fun setDataProvider(provider: DataProvider?) {
    dataManager.setTestDataProvider(provider)
  }

  fun setDataProvider(provider: DataProvider?, parentDisposable: Disposable?) {
    dataManager.setTestDataProvider(provider, parentDisposable!!)
  }

  fun getData(dataId: String) = dataManager.dataContext.getData(dataId)

  fun disposeApp() {
    ApplicationManager.getApplication().invokeAndWait {
      disposeInstance()
    }
  }

  fun dispose() {
    disposeInstance()
  }
}

private fun loadTestApp() {
  Main.setFlags(arrayOf("inspect", "", "", ""))
  assert(Main.isHeadless())
  assert(Main.isCommandLine())
  PluginManagerCore.isUnitTestMode = true
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)

  val loadedPluginFuture = CompletableFuture.supplyAsync(Supplier {
    PluginManagerCore.getLoadedPlugins(IdeaTestApplication::class.java.classLoader)
  }, AppExecutorUtil.getAppExecutorService())

  if (EventQueue.isDispatchThread()) {
    StartupUtil.replaceSystemEventQueue(logger<IdeaTestApplication>())
  }
  else {
    replaceIdeEventQueueSafely()
  }

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

fun replaceIdeEventQueueSafely() {
  if (Toolkit.getDefaultToolkit().systemEventQueue is IdeEventQueue) {
    return
  }
  if (SwingUtilities.isEventDispatchThread()) {
    throw IllegalStateException("must not call under EDT")
  }

  AWTAutoShutdown.getInstance().notifyThreadBusy(Thread.currentThread())
  // in JDK 1.6 java.awt.EventQueue.push() causes slow painful death of current EDT
  // so we have to wait through its agony to termination
  UIUtil.pump()
  EventQueue.invokeAndWait { IdeEventQueue.getInstance() }
  EventQueue.invokeAndWait(EmptyRunnable.getInstance())
  EventQueue.invokeAndWait(EmptyRunnable.getInstance())
}