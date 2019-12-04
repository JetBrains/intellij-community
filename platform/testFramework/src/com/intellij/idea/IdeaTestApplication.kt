// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.DataManager
import com.intellij.ide.GeneratedSourceFileChangeTracker
import com.intellij.ide.GeneratedSourceFileChangeTrackerImpl
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.ide.structureView.StructureViewFactory
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.DocumentReferenceManagerImpl
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.IconManager
import com.intellij.ui.UiInterceptors
import com.intellij.util.ReflectionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.ref.GCUtil
import com.intellij.util.ui.UIUtil
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFilePointerProvider.Companion.getInstance
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFilePointerProviderImpl
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModuleRootComponent
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeProjectLifecycleListener.Companion.enabled
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeRootsWatcher
import junit.framework.AssertionFailedError
import org.jetbrains.annotations.ApiStatus
import sun.awt.AWTAutoShutdown
import java.awt.EventQueue
import java.awt.Toolkit
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import javax.swing.SwingUtilities
import javax.swing.Timer

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

private inline fun MutableList<Throwable>.run(executor: () -> Unit) {
  try {
    executor()
  }
  catch (e: CompoundRuntimeException) {
    addAll(e.exceptions)
  }
  catch (e: Throwable) {
    add(e)
  }
}

inline fun <reified T : Any, reified TI : Any> Application.serviceIfCreated(): TI? = this.getServiceIfCreated(T::class.java) as TI?


private var testCounter = 0

// Kotlin allows to easily debug code and to get clear and short stack traces
@ApiStatus.Internal
fun tearDownProjectAndApp(project: Project, appManager: IdeaTestApplication) {
  val isLightProject = ProjectManagerImpl.isLight(project)

  val l = mutableListOf<Throwable>()
  val app = ApplicationManager.getApplication()

  l.run { app.serviceIfCreated<FileTypeManager, FileTypeManagerImpl>()?.drainReDetectQueue() }
  l.run {
    if (isLightProject) {
      project.serviceIfCreated<AutoPopupController>()?.cancelAllRequests()
    }
  }
  l.run { CodeStyle.dropTemporarySettings(project) }
  l.run { checkJavaSwingTimersAreDisposed() }
  l.run { UsefulTestCase.doPostponedFormatting(project) }
  l.run { LookupManager.hideActiveLookup(project) }
  l.run {
    if (isLightProject) {
      (project.serviceIfCreated<StartupManager>() as StartupManagerImpl?)?.prepareForNextTest()
    }
  }
  l.run {
    if (isLightProject) {
      LightPlatformTestCase.tearDownSourceRoot(project)
    }
  }
  l.run {
    WriteCommandAction.runWriteCommandAction(project) {
      app.serviceIfCreated<FileDocumentManager, FileDocumentManagerImpl>()?.dropAllUnsavedDocuments()
    }
  }
  l.run { project.serviceIfCreated<EditorHistoryManager>()?.removeAllFiles() }
  l.run {
    if (project.serviceIfCreated<PsiManager>()?.isDisposed == true) {
      throw IllegalStateException("PsiManager must be not disposed")
    }
  }
  l.run { LightPlatformTestCase.clearEncodingManagerDocumentQueue() }
  l.run { LightPlatformTestCase.checkAssertions() }
  l.run { LightPlatformTestCase.clearUncommittedDocuments(project) }

  l.run { app.serviceIfCreated<HintManager, HintManagerImpl>()?.cleanup() }

  l.run { (UndoManager.getGlobalInstance() as UndoManagerImpl).dropHistoryInTests() }
  l.run { (UndoManager.getInstance(project) as UndoManagerImpl).dropHistoryInTests() }

  l.run { app.serviceIfCreated<DocumentReferenceManager, DocumentReferenceManagerImpl>()?.cleanupForNextTest() }

  l.run { project.serviceIfCreated<TemplateDataLanguageMappings>()?.cleanupForNextTest() }
  l.run { (project.serviceIfCreated<PsiManager>() as PsiManagerImpl?)?.cleanupForNextTest() }
  l.run { (project.serviceIfCreated<StructureViewFactory>() as StructureViewFactoryImpl?)?.cleanupForNextTest() }

  l.run { waitForProjectLeakingThreads(project) }
  l.run {
    WriteAction.runAndWait<RuntimeException> {
      if (!enabled(project)) {
        return@runAndWait
      }

      for (module in ModuleManager.getInstance(project).modules) {
        (getInstance(module) as LegacyBridgeFilePointerProviderImpl).disposeAndClearCaches()
        for (orderEntry in ModuleRootManager.getInstance(module).orderEntries) {
          if (orderEntry is LibraryOrderEntry) {
            if (orderEntry.isModuleLevel) {
              (orderEntry.library as LegacyBridgeLibraryImpl).filePointerProvider.disposeAndClearCaches()
            }
          }
        }
        (ModuleRootManager.getInstance(module) as LegacyBridgeModuleRootComponent).dropCaches()
      }
      (getInstance(project) as LegacyBridgeFilePointerProviderImpl).disposeAndClearCaches()
      for (library in LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries) {
        (library as LegacyBridgeLibraryImpl).filePointerProvider.disposeAndClearCaches()
      }
      LegacyBridgeRootsWatcher.getInstance(project).clear()
    }
  }

  l.run { (ProjectManager.getInstance() as ProjectManagerImpl).forceCloseProject(project, !isLightProject) }

  l.run { appManager.setDataProvider(null) }
  l.run { UiInterceptors.clear() }
  l.run { CompletionProgressIndicator.cleanupForNextTest() }
  l.run {
    if (testCounter++ % 100 == 0) {
      // some tests are written in Groovy, and running all of them may result in some 40M of memory wasted on bean infos
      // so let's clear the cache every now and then to ensure it doesn't grow too large
      GCUtil.clearBeanInfoCache()
    }
  }

  CompoundRuntimeException.throwIfNotEmpty(l)
}

@ReviseWhenPortedToJDK("9")
private fun checkJavaSwingTimersAreDisposed() {
  val timerQueueClass = Class.forName("javax.swing.TimerQueue")
  val sharedInstance = timerQueueClass.getMethod("sharedInstance")
  sharedInstance.isAccessible = true
  val timerQueue = sharedInstance.invoke(null)
  val delayQueue = ReflectionUtil.getField(timerQueueClass, timerQueue, DelayQueue::class.java, "queue")
  val timer = delayQueue.peek()
  if (timer == null) {
    return
  }

  val delay = timer.getDelay(TimeUnit.MILLISECONDS)
  var text = "(delayed for ${delay}ms)"
  val getTimer = ReflectionUtil.getDeclaredMethod(timer.javaClass, "getTimer")
  val swingTimer = getTimer!!.invoke(timer) as Timer
  text = "Timer (listeners: ${Arrays.asList(*swingTimer.actionListeners)}) $text"
  try {
    throw AssertionFailedError("Not disposed javax.swing.Timer: $text; queue:$timerQueue")
  }
  finally {
    swingTimer.stop()
  }
}

@ApiStatus.Internal
@JvmOverloads
fun waitForProjectLeakingThreads(project: Project, timeout: Long = 10, timeUnit: TimeUnit = TimeUnit.SECONDS) {
  if (project is ProjectImpl) {
    project.stopServicePreloading()
  }

  NonBlockingReadActionImpl.cancelAllTasks()
  (project.serviceIfCreated<GeneratedSourceFileChangeTracker>() as GeneratedSourceFileChangeTrackerImpl?)?.cancelAllAndWait(timeout, timeUnit)
}