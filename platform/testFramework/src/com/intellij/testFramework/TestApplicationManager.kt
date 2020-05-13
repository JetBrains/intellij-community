// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ide.DataManager
import com.intellij.ide.GeneratedSourceFileChangeTracker
import com.intellij.ide.GeneratedSourceFileChangeTrackerImpl
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.ide.structureView.StructureViewFactory
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl
import com.intellij.idea.StartupUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.ui.UiInterceptors
import com.intellij.util.ReflectionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.ref.GCUtil
import com.intellij.util.ui.UIUtil
import com.intellij.workspace.legacyBridge.LegacyBridgeTestFrameworkUtils
import junit.framework.AssertionFailedError
import org.jetbrains.annotations.ApiStatus
import sun.awt.AWTAutoShutdown
import java.awt.EventQueue
import java.awt.Toolkit
import java.util.concurrent.DelayQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.Timer

class TestApplicationManager private constructor() {
  companion object {
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
          StartupUtil.replaceSystemEventQueue(logger<TestApplicationManager>())
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
    runInEdtAndWait {
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
fun tearDownProjectAndApp(project: Project, appManager: TestApplicationManager? = null) {
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
  l.run { LegacyBridgeTestFrameworkUtils.dropCachesOnTeardown(project) }

  l.run { (ProjectManager.getInstance() as ProjectManagerImpl).forceCloseProject(project, !isLightProject) }
  l.run { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }

  l.run { (appManager ?: TestApplicationManager.getInstanceIfCreated())?.setDataProvider(null) }
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

/**
 * Disposes the application (it also stops some application-related threads)
 * and checks for project leaks.
 */
fun disposeApplicationAndCheckForLeaks() {
  val l = mutableListOf<Throwable>()

  runInEdtAndWait {
    l.run { PlatformTestUtil.cleanupAllProjects() }
    l.run { UIUtil.dispatchAllInvocationEvents() }

    l.run {
      val app = ApplicationManager.getApplication() as? ApplicationImpl
      if (app != null) {
        println(app.writeActionStatistics())
      }
      println(ActionUtil.ActionPauses.STAT.statistics())
      println((AppExecutorUtil.getAppScheduledExecutorService() as AppScheduledExecutorService).statistics())
      println("ProcessIOExecutorService threads created: ${(ProcessIOExecutorService.INSTANCE as ProcessIOExecutorService).threadCounter}")
    }

    l.run { UsefulTestCase.waitForAppLeakingThreads(10, TimeUnit.SECONDS) }

    l.run {
      try {
        LeakHunter.checkNonDefaultProjectLeak()
      }
      catch (e: AssertionError) {
        HeavyPlatformTestCase.publishHeapDump("leakedProjects")
        throw e
      }
      catch (e: Exception) {
        HeavyPlatformTestCase.publishHeapDump("leakedProjects")
        throw e
      }
    }

    l.run { TestApplicationManager.getInstanceIfCreated()?.dispose() }
    l.run { UIUtil.dispatchAllInvocationEvents() }
  }

  l.run {
    try {
      Disposer.assertIsEmpty(true)
    }
    catch (e: AssertionError) {
      HeavyPlatformTestCase.publishHeapDump("disposerNonEmpty")
      throw e
    }
    catch (e: Exception) {
      HeavyPlatformTestCase.publishHeapDump("disposerNonEmpty")
      throw e
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
  if (project is ProjectImpl) {
    project.stopServicePreloading()
  }

  (project.serviceIfCreated<GeneratedSourceFileChangeTracker>() as GeneratedSourceFileChangeTrackerImpl?)?.cancelAllAndWait(timeout, timeUnit)
}