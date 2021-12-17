// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.testFramework

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.doLoadApp
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.*
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.ide.structureView.StructureViewFactory
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl
import com.intellij.idea.StartupUtil
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
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.UiInterceptors
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.ReflectionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.lang.Java11Shim
import com.intellij.util.ref.GCUtil
import com.intellij.util.throwIfNotEmpty
import com.intellij.util.ui.UIUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import junit.framework.AssertionFailedError
import org.jetbrains.annotations.ApiStatus
import sun.awt.AWTAutoShutdown
import java.awt.EventQueue
import java.awt.Toolkit
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.DelayQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.Timer

class TestApplicationManager private constructor() {
  companion object {
    init {
      Java11Shim.INSTANCE = StartupUtil.Java11ShimImpl()
      ExtensionNotApplicableException.useFactoryWithStacktrace()
    }

    @Volatile
    private var ourInstance: TestApplicationManager? = null

    @Volatile
    private var bootstrapError: Throwable? = null
    private val isBootstrappingAppNow = AtomicBoolean()

    private val dataManager: HeadlessDataManager
      get() = DataManager.getInstance() as HeadlessDataManager

    @JvmStatic
    fun getInstance(): TestApplicationManager {
      var result = ourInstance
      if (result == null) {
        try {
          result = createInstance()
        }
        catch (e: Throwable) {
          bootstrapError = e
          isBootstrappingAppNow.set(false)
          throw e
        }
      }
      return result
    }

    @JvmStatic
    fun getInstanceIfCreated() = ourInstance

    @Synchronized
    private fun createInstance(): TestApplicationManager {
      var result = ourInstance
      if (result != null) {
        return result
      }

      bootstrapError?.let {
        throw it
      }

      StartUpMeasurer.disable()

      if (!isBootstrappingAppNow.compareAndSet(false, true)) {
        throw IllegalStateException("App bootstrap is already in process")
      }

      HeavyPlatformTestCase.doAutodetectPlatformPrefix()
      doLoadApp {
        if (EventQueue.isDispatchThread()) {
          // replaces system event queue
          IdeEventQueue.getInstance()
        }
        else {
          replaceIdeEventQueueSafely()
        }
      }
      isBootstrappingAppNow.set(false)
      result = TestApplicationManager()
      ourInstance = result
      return result
    }
  }

  fun setDataProvider(provider: DataProvider?) {
    dataManager.setTestDataProvider(provider)
  }

  fun setDataProvider(provider: DataProvider?, parentDisposable: Disposable?) {
    dataManager.setTestDataProvider(provider, parentDisposable!!)
  }

  fun getData(dataId: String) = dataManager.dataContext.getData(dataId)

  fun dispose() {
    val app = ApplicationManager.getApplication() as ApplicationImpl? ?: return
    app.invokeAndWait {
      // `ApplicationManager#ourApplication` will be automatically set to `null`
      app.disposeContainer()
      ourInstance = null
    }
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

inline fun <reified T : Any, reified TI : Any> Application.serviceIfCreated(): TI? = this.getServiceIfCreated(T::class.java) as? TI

private var testCounter = 0

// Kotlin allows to easily debug code and to get clear and short stack traces
@ApiStatus.Internal
fun tearDownProjectAndApp(project: Project) {
  if (project.isDisposed) {
    return
  }

  val isLightProject = ProjectManagerImpl.isLight(project)
  val l = mutableListOf<Throwable>()
  val app = ApplicationManager.getApplication()

  l.catchAndStoreExceptions { app.serviceIfCreated<FileTypeManager, FileTypeManagerImpl>()?.drainReDetectQueue() }
  l.catchAndStoreExceptions {
    if (isLightProject) {
      project.serviceIfCreated<AutoPopupController>()?.cancelAllRequests()
    }
  }
  l.catchAndStoreExceptions { CodeStyle.dropTemporarySettings(project) }
  l.catchAndStoreExceptions { checkJavaSwingTimersAreDisposed() }
  l.catchAndStoreExceptions { UsefulTestCase.doPostponedFormatting(project) }
  l.catchAndStoreExceptions { LookupManager.hideActiveLookup(project) }
  l.catchAndStoreExceptions {
    if (isLightProject) {
      (project.serviceIfCreated<StartupManager>() as StartupManagerImpl?)?.prepareForNextTest()
    }
  }
  l.catchAndStoreExceptions {
    if (isLightProject) {
      LightPlatformTestCase.tearDownSourceRoot(project)
    }
  }
  l.catchAndStoreExceptions {
    WriteCommandAction.runWriteCommandAction(project) {
      app.serviceIfCreated<FileDocumentManager, FileDocumentManagerImpl>()?.dropAllUnsavedDocuments()
    }
  }
  l.catchAndStoreExceptions { project.serviceIfCreated<EditorHistoryManager>()?.removeAllFiles() }
  l.catchAndStoreExceptions {
    if (project.serviceIfCreated<PsiManager>()?.isDisposed == true) {
      throw IllegalStateException("PsiManager must be not disposed")
    }
  }
  l.catchAndStoreExceptions { LightPlatformTestCase.clearEncodingManagerDocumentQueue() }
  l.catchAndStoreExceptions { LightPlatformTestCase.checkAssertions() }
  l.catchAndStoreExceptions { LightPlatformTestCase.clearUncommittedDocuments(project) }

  l.catchAndStoreExceptions { app.serviceIfCreated<HintManager, HintManagerImpl>()?.cleanup() }

  l.catchAndStoreExceptions { (UndoManager.getGlobalInstance() as UndoManagerImpl).dropHistoryInTests() }
  l.catchAndStoreExceptions { (UndoManager.getInstance(project) as UndoManagerImpl).dropHistoryInTests() }

  l.catchAndStoreExceptions { app.serviceIfCreated<DocumentReferenceManager, DocumentReferenceManagerImpl>()?.cleanupForNextTest() }

  l.catchAndStoreExceptions { project.serviceIfCreated<TemplateDataLanguageMappings>()?.cleanupForNextTest() }
  l.catchAndStoreExceptions { (project.serviceIfCreated<PsiManager>() as PsiManagerImpl?)?.cleanupForNextTest() }
  l.catchAndStoreExceptions { (project.serviceIfCreated<StructureViewFactory>() as StructureViewFactoryImpl?)?.cleanupForNextTest() }

  l.catchAndStoreExceptions { waitForProjectLeakingThreads(project) }
  l.catchAndStoreExceptions { dropModuleRootCaches(project) }

  // reset data provider before disposing project to ensure that disposed project is not accessed
  l.catchAndStoreExceptions { TestApplicationManager.getInstanceIfCreated()?.setDataProvider(null) }
  l.catchAndStoreExceptions {
    ProjectManagerEx.getInstanceEx().forceCloseProject(project)
  }
  l.catchAndStoreExceptions { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }

  l.catchAndStoreExceptions { UiInterceptors.clear() }
  l.catchAndStoreExceptions { CompletionProgressIndicator.cleanupForNextTest() }
  l.catchAndStoreExceptions {
    if (testCounter++ % 100 == 0) {
      // some tests are written in Groovy, and running all of them may result in some 40M of memory wasted on bean infos
      // so let's clear the cache every now and then to ensure it doesn't grow too large
      GCUtil.clearBeanInfoCache()
    }
  }

  throwIfNotEmpty(l)
}

private fun dropModuleRootCaches(project: Project) {
  WriteAction.runAndWait<RuntimeException> {
    for (module in ModuleManager.getInstance(project).modules) {
      (ModuleRootManager.getInstance(module) as ModuleRootComponentBridge).dropCaches()
    }
  }
}

/**
 * Disposes the application (it also stops some application-related threads)
 * and checks for project leaks.
 */
fun disposeApplicationAndCheckForLeaks() {
  val l = mutableListOf<Throwable>()

  runInEdtAndWait {
    l.catchAndStoreExceptions { PlatformTestUtil.cleanupAllProjects() }
    l.catchAndStoreExceptions { UIUtil.dispatchAllInvocationEvents() }

    l.catchAndStoreExceptions {
      println((AppExecutorUtil.getAppScheduledExecutorService() as AppScheduledExecutorService).statistics())
      println("ProcessIOExecutorService threads created: ${(ProcessIOExecutorService.INSTANCE as ProcessIOExecutorService).threadCounter}")
    }

    l.catchAndStoreExceptions {
      val app = ApplicationManager.getApplication() as? ApplicationImpl
      app?.messageBus?.syncPublisher(AppLifecycleListener.TOPIC)?.appWillBeClosed(false)
    }

    l.catchAndStoreExceptions { UsefulTestCase.waitForAppLeakingThreads(10, TimeUnit.SECONDS) }

    l.catchAndStoreExceptions {
      try {
        if (ApplicationManager.getApplication() != null) {
          LeakHunter.checkNonDefaultProjectLeak()
        }
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

    l.catchAndStoreExceptions { TestApplicationManager.getInstanceIfCreated()?.dispose() }
    l.catchAndStoreExceptions { UIUtil.dispatchAllInvocationEvents() }
  }

  l.catchAndStoreExceptions {
    try {
      Disposer.assertIsEmpty(true)
    }
    catch (e: AssertionError) {
      publishHeapDump("disposerNonEmpty")
      throw e
    }
    catch (e: Exception) {
      publishHeapDump("disposerNonEmpty")
      throw e
    }
  }

  throwIfNotEmpty(l)
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
  text = "Timer (listeners: ${listOf(*swingTimer.actionListeners)}) $text"
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
  if (project is ComponentManagerImpl) {
    project.stopServicePreloading()
  }

  (project.serviceIfCreated<GeneratedSourceFileChangeTracker>() as GeneratedSourceFileChangeTrackerImpl?)?.cancelAllAndWait(timeout,
                                                                                                                            timeUnit)
}

fun publishHeapDump(fileNamePrefix: String): String {
  val fileName = "$fileNamePrefix.hprof.zip"
  val dumpFile = Paths.get(System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir")), fileName)
  try {
    Files.deleteIfExists(dumpFile)
    MemoryDumpHelper.captureMemoryDumpZipped(dumpFile)
  }
  catch (e: Exception) {
    e.printStackTrace()
  }

  val dumpPath = dumpFile.toAbsolutePath().toString()
  println("##teamcity[publishArtifacts '$dumpPath']")
  return dumpPath
}