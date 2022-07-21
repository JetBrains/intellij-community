// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.Main
import com.intellij.idea.callAppInitialized
import com.intellij.idea.getAppInitListeners
import com.intellij.idea.initConfigurationStore
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.impl.DocumentReferenceManagerImpl
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.ex.ProjectManagerEx
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
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.UITestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.UiInterceptors
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.time.Duration
import java.util.concurrent.TimeUnit

private val LOG: Logger = Logger.getInstance("com.intellij.testFramework.common.TestApplicationKt")

private var applicationInitializationResult: Result<Unit>? = null

val isApplicationInitialized: Boolean
  get() = applicationInitializationResult?.isSuccess == true

@TestOnly
@Internal
fun initTestApplication() {
  (applicationInitializationResult ?: doInitTestApplication()).getOrThrow()
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
          )
        }
        app.loadComponents()
      }

      callAppInitialized(getAppInitListeners(app))
    }

    StartUpMeasurer.setCurrentState(LoadingState.APP_STARTED)
    (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()
  }
  catch (e: InterruptedException) {
    throw e.cause ?: e
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

  val index = application.serviceIfCreated<FileBasedIndex>() as? FileBasedIndexImpl
  index?.changedFilesCollector?.waitForVfsEventsExecuted(timeout, timeUnit)

  val commitThread = application.serviceIfCreated<DocumentCommitProcessor>() as? DocumentCommitThread
  commitThread?.waitForAllCommits(timeout, timeUnit)
}

@TestOnly
@Internal
fun disposeTestApplication() {
  EDT.assertIsEdt()
  val app = ApplicationManager.getApplication() as ApplicationImpl
  app.disposeContainer() // `ApplicationManager#ourApplication` will be automatically set to `null`
  applicationInitializationResult = null
}
