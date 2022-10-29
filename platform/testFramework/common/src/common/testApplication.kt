// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.testFramework.common

import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.idea.*
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.impl.RwLockHolder
import com.intellij.openapi.command.impl.DocumentReferenceManagerImpl
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyBean.Companion.addKeysFromPlugins
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
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
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EdtInvocationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import sun.awt.AWTAutoShutdown
import java.time.Duration
import java.util.concurrent.TimeUnit

private var applicationInitializationResult: Result<Unit>? = null
const val LEAKED_PROJECTS = "leakedProjects"

val isApplicationInitialized: Boolean
  get() = applicationInitializationResult?.isSuccess == true

@TestOnly
@Internal
fun initTestApplication(): Result<Unit> {
  return (applicationInitializationResult ?: doInitTestApplication())
}

@TestOnly
@Synchronized
private fun doInitTestApplication(): Result<Unit> {
  applicationInitializationResult?.let {
    return it
  }
  val result = runCatching {
    loadApp()
  }
  applicationInitializationResult = result
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
  val isHeadless = UITestUtil.getAndSetHeadlessProperty()
  AppMode.setHeadlessInTestMode(isHeadless)
  PluginManagerCore.isUnitTestMode = true
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)
  PluginManagerCore.scheduleDescriptorLoading(GlobalScope)
  setupEventQueue.run()
  loadAppInUnitTestMode(isHeadless)
}

@TestOnly
private fun loadAppInUnitTestMode(isHeadless: Boolean) {
  val loadedModuleFuture = PluginManagerCore.getInitPluginFuture()

  val rwLockHolder = RwLockHolder()
  val awtBusyThread = AppScheduledExecutorService.getPeriodicTasksThread()
  EdtInvocationManager.invokeAndWaitIfNeeded {
    // Instantiate `AppDelayQueue` which starts "periodic tasks thread" which we'll mark busy to prevent this EDT from dying.
    // That thread was chosen because we know for sure it's running. Needed for EDT not to exit suddenly
    AWTAutoShutdown.getInstance().notifyThreadBusy(awtBusyThread)
    rwLockHolder.initialize(Thread.currentThread())
  }

  val app = ApplicationImpl(isHeadless, rwLockHolder)

  Disposer.register(app) {
    AWTAutoShutdown.getInstance().notifyThreadFree(awtBusyThread)
  }

  if (SystemProperties.getBooleanProperty("tests.assertOnMissedCache", true)) {
    RecursionManager.assertOnMissedCache(app)
  }

  try {
    // 40 seconds - tests maybe executed on cloud agents where I/O is very slow
    val pluginSet = loadedModuleFuture.asCompletableFuture().get(40, TimeUnit.SECONDS)
    app.registerComponents(modules = pluginSet.getEnabledModules(), app = app, precomputedExtensionModel = null, listenerCallbacks = null)

    initConfigurationStore(app)

    addKeysFromPlugins()
    Registry.markAsLoaded()

    if (EDT.isCurrentThreadEdt()) {
      runBlockingModal(ModalTaskOwner.guess(), "") {
        preloadServicesAndCallAppInitializedListeners(app, pluginSet)
      }
    }
    else {
      runBlocking(Dispatchers.Default) {
        preloadServicesAndCallAppInitializedListeners(app, pluginSet)
      }
    }

    StartUpMeasurer.setCurrentState(LoadingState.APP_STARTED)
    (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()
  }
  catch (e: InterruptedException) {
    throw e.cause ?: e
  }
}

private suspend fun preloadServicesAndCallAppInitializedListeners(app: ApplicationImpl, pluginSet: PluginSet) {
  coroutineScope {
    withTimeout(Duration.ofSeconds(40).toMillis()) {
      preloadCriticalServices(app)
      app.preloadServices(
        modules = pluginSet.getEnabledModules(),
        activityPrefix = "",
        syncScope = this,
      )
    }

    app.createInitOldComponentsTask()?.let { loadComponentInEdtTask ->
      withContext(Dispatchers.EDT) {
        loadComponentInEdtTask()
      }
    }
    app.loadComponents()
  }

  coroutineScope {
    callAppInitialized(getAppInitializedListeners(app), app.coroutineScope)
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
    { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() },
    { UiInterceptors.clear() },
    {
      runInEdtAndWait {
        CompletionProgressIndicator.cleanupForNextTest()
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
      editorFactory.releaseEditor(editor)
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
    if (localFileSystem != null) {
      runInEdtAndWait {
        (localFileSystem as LocalFileSystemBase).cleanupForNextTest()
      }
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
    val heapDump = publishHeapDump(LEAKED_PROJECTS)
    throw AssertionError(e)
  }
  catch (e: Exception) {
    val heapDump = publishHeapDump(LEAKED_PROJECTS)
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
  applicationInitializationResult = null
}
