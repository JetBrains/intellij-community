// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "RAW_RUN_BLOCKING")

package com.intellij.testFramework.common

import com.intellij.BundleBase
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.COROUTINE_DUMP_HEADER
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.AppMode
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.AWTExceptionHandler
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.impl.DocumentReferenceManagerImpl
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.P3SupportInstaller
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.platform.ide.bootstrap.callAppInitialized
import com.intellij.platform.ide.bootstrap.getAppInitializedListeners
import com.intellij.platform.ide.bootstrap.initConfigurationStore
import com.intellij.platform.ide.bootstrap.preloadCriticalServices
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.DocumentCommitProcessor
import com.intellij.psi.impl.DocumentCommitThread
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexImpl
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.UITestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.UiInterceptors
import com.intellij.util.SystemProperties
import com.intellij.util.WalkingState
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EdtInvocationManager
import com.jetbrains.JBR
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import sun.awt.AWTAutoShutdown
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.coroutines.jvm.internal.CoroutineDumpState

private var appInitResult: Result<Unit>? = null
const val LEAKED_PROJECTS: String = "leakedProjects"

val isApplicationInitialized: Boolean
  get() = appInitResult?.isSuccess == true

@TestOnly
@Internal
fun initTestApplication(): Result<Unit> {
  return (appInitResult ?: doInitTestApplication())
}

@TestOnly
@Synchronized
private fun doInitTestApplication(): Result<Unit> {
  appInitResult?.let {
    return it
  }
  val result = runCatching {
    loadApp()
  }
  appInitResult = result
  return result
}

@TestOnly
@Internal
fun loadApp() {
  loadApp(UITestUtil::setupEventQueue)
}

@TestOnly
@OptIn(DelicateCoroutinesApi::class)
@Internal
fun loadApp(setupEventQueue: Runnable) {
  // Open Telemetry file will be located at ../system/test/log/opentelemetry.json (alongside open-telemetry-metrics.*.csv)
  System.setProperty("idea.diagnostic.opentelemetry.file",
                     PathManager.getLogDir().resolve("opentelemetry.json").toAbsolutePath().toString())

  // if BB in classpath
  enableCoroutineDump()
  CoroutineDumpState.install()
  JBR.getJstack()?.includeInfoFrom {
    """
    $COROUTINE_DUMP_HEADER
    ${dumpCoroutines(stripDump = false)}
    """.trimIndent()
  }
  val isHeadless = UITestUtil.getAndSetHeadlessProperty()
  AppMode.setHeadlessInTestMode(isHeadless)
  PluginManagerCore.isUnitTestMode = true
  P3SupportInstaller.seal()
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)
  PluginManagerCore.scheduleDescriptorLoading(GlobalScope)
  setupEventQueue.run()
  loadAppInUnitTestMode(isHeadless)
}

@TestOnly
private fun loadAppInUnitTestMode(isHeadless: Boolean) {
  val loadedModuleFuture = PluginManagerCore.initPluginFuture

  val awtBusyThread = AppScheduledExecutorService.getPeriodicTasksThread()
  EdtInvocationManager.invokeAndWaitIfNeeded {
    // Instantiate `AppDelayQueue` which starts "periodic tasks thread" which we'll mark busy to prevent this EDT from dying.
    // That thread was chosen because we know for sure it's running. Needed for EDT not to exit suddenly
    AWTAutoShutdown.getInstance().notifyThreadBusy(awtBusyThread)
  }

  val app = ApplicationImpl(isHeadless)
  Disposer.register(app) {
    AWTAutoShutdown.getInstance().notifyThreadFree(awtBusyThread)
  }

  BundleBase.assertOnMissedKeys(true)
  // do not crash AWT on exceptions
  AWTExceptionHandler.register()
  Disposer.setDebugMode(true)
  Logger.setUnitTestMode()
  WalkingState.setUnitTestMode()

  if (SystemProperties.getBooleanProperty("tests.assertOnMissedCache", true)) {
    RecursionManager.assertOnMissedCache(app)
  }

  try {
    // 40 seconds - tests maybe executed on cloud agents where I/O is very slow
    val pluginSet = loadedModuleFuture.asCompletableFuture().get(40, TimeUnit.SECONDS)
    app.registerComponents(modules = pluginSet.getEnabledModules(), app = app)

    val task = suspend {
      initConfigurationStore(app, emptyList())

      RegistryManager.getInstance() // to trigger RegistryKeyBean.addKeysFromPlugins exactly once per run
      Registry.markAsLoaded()

      preloadServicesAndCallAppInitializedListeners(app)
    }

    if (EDT.isCurrentThreadEdt()) {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
        task()
      }
    }
    else {
      runBlocking(Dispatchers.Default) {
        task()
      }
    }

    LoadingState.setCurrentState(LoadingState.APP_STARTED)
    (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()
  }
  catch (e: InterruptedException) {
    throw e.cause ?: e
  }
}

private suspend fun preloadServicesAndCallAppInitializedListeners(app: ApplicationImpl) {
  coroutineScope {
    withTimeout(Duration.ofSeconds(40).toMillis()) {
      val pathMacroJob = preloadCriticalServices(
        app = app,
        asyncScope = app.getCoroutineScope(),
        appRegistered = CompletableDeferred(value = null),
        initAwtToolkitAndEventQueueJob = null,
      )
      launch {
        pathMacroJob.join()
        app.serviceAsync<LogLevelConfigurationManager>()
      }
    }

    @Suppress("TestOnlyProblems")
    callAppInitialized(getAppInitializedListeners(app))

    LoadingState.setCurrentState(LoadingState.COMPONENTS_LOADED)
  }
}

/**
 * This function is intended to be run after each test.
 *
 * This function combines various clean up and assertion pieces,
 * which were spread across TestCase inheritors and fixtures (JUnit 3).
 * JUnit 5 integration uses only this function to clean up the state of the shared application.
 */
@TestOnly
@Internal
fun Application.cleanApplicationState() {
  var error: Throwable? = null
  fun addError(e: Throwable) {
    if (error == null) {
      error = e
    }
    else {
      error!!.addSuppressed(e)
    }
  }

  runCatching {
    waitForAppLeakingThreads(application = this, timeout = 10, timeUnit = TimeUnit.SECONDS)
  }.onFailure(::addError)

  cleanApplicationStateCatching()?.let(::addError)
  runCatching(::checkEditorsReleased).onFailure(::addError)
  runCatching(Application::cleanupApplicationCaches).onFailure(::addError)
  error?.let { throw it }
}

private inline fun <reified T : Any> Application.serviceIfCreated(): T? = this.getServiceIfCreated(T::class.java)

@TestOnly
@Internal
fun Application.cleanApplicationStateCatching(): Throwable? {
  return runAllCatching(
    { (serviceIfCreated<FileTypeManager>() as? FileTypeManagerImpl)?.drainReDetectQueue() },
    { clearEncodingManagerDocumentQueue() },
    { (serviceIfCreated<HintManager>() as? HintManagerImpl)?.cleanup() },
    {
      runInEdtAndWait {
        (serviceIfCreated<UndoManager>() as? UndoManagerImpl)?.dropHistoryInTests()
      }
    },
    { (serviceIfCreated<DocumentReferenceManager>() as? DocumentReferenceManagerImpl)?.cleanupForNextTest() },
    { UiInterceptors.clear() },
    {
      runInEdtAndWait {
        CompletionProgressIndicator.cleanupForNextTest()
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      }
    },
  )
}

@TestOnly
@Internal
fun Application.clearEncodingManagerDocumentQueue() {
  (serviceIfCreated<EncodingManager>() as? EncodingManagerImpl)?.clearDocumentQueue()
}

@TestOnly
@Internal
fun Application.checkEditorsReleased() {
  val editorFactory = serviceIfCreated<EditorFactory>() ?: return
  val actions = mutableListOf<() -> Unit>()
  for (editor in editorFactory.allEditors) {
    actions.add {
      EditorFactoryImpl.throwNotReleasedError(editor)
    }
    actions.add {
      ApplicationManager.getApplication().invokeAndWait {
        editorFactory.releaseEditor(editor)
      }
    }
  }
  runAll(actions)
}

@TestOnly
@Internal
fun Application.clearIdCache() {
  val managingFS = serviceIfCreated<ManagingFS>() ?: return
  (managingFS as PersistentFS).clearIdCache()
}

@TestOnly
@Internal
fun Application.cleanupApplicationCaches() {
  val projectManager = ProjectManagerEx.getInstanceExIfCreated()
  if (projectManager != null && projectManager.isDefaultProjectInitialized) {
    val defaultProject = projectManager.defaultProject
    runInEdtAndWait {
      (PsiManager.getInstance(defaultProject) as PsiManagerImpl).cleanupForNextTest()
    }
  }
  (serviceIfCreated<FileBasedIndex>() as? FileBasedIndexImpl)?.cleanupForNextTest()
  if (serviceIfCreated<VirtualFileManager>() != null) {
    val localFileSystem = LocalFileSystem.getInstance()
    runInEdtAndWait {
      (localFileSystem as LocalFileSystemBase).cleanupForNextTest()
    }
  }
}

@TestOnly
@Internal
fun assertNonDefaultProjectsAreNotLeaked() {
  try {
    LeakHunter.checkNonDefaultProjectLeak()
  }
  catch (e: AssertionError) {
    publishHeapDump(LEAKED_PROJECTS)
    throw AssertionError(e)
  }
  catch (e: Exception) {
    publishHeapDump(LEAKED_PROJECTS)
    throw AssertionError(e)
  }
}

@TestOnly
fun waitForAppLeakingThreads(application: Application, timeout: Long, timeUnit: TimeUnit) {
  require(!application.isDisposed)

  val index = application.serviceIfCreated<FileBasedIndex>() as? FileBasedIndexImpl
  index?.changedFilesCollector?.waitForVfsEventsExecuted(timeout, timeUnit)

  val commitThread = application.serviceIfCreated<DocumentCommitProcessor>() as? DocumentCommitThread
  commitThread?.waitForAllCommits(timeout, timeUnit)

  val stubIndex = application.serviceIfCreated<StubIndex>() as? StubIndexImpl
  stubIndex?.waitUntilStubIndexedInitialized()
}

@TestOnly
@Internal
fun disposeTestApplication() {
  EDT.assertIsEdt()
  val app = ApplicationManager.getApplication() as ApplicationImpl
  app.disposeContainer() // `ApplicationManager#ourApplication` will be automatically set to `null`
  appInitResult = null
}
