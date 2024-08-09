// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.DataManager
import com.intellij.ide.GeneratedSourceFileChangeTracker
import com.intellij.ide.GeneratedSourceFileChangeTrackerImpl
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.ide.structureView.StructureViewFactory
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.common.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.ref.GCUtil
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit

class TestApplicationManager private constructor() {
  companion object {
    init {
      initializeTestEnvironment()
    }

    private val ourInstance = TestApplicationManager()

    private val dataManager: HeadlessDataManager
      get() = DataManager.getInstance() as HeadlessDataManager

    @JvmStatic
    fun getInstance(): TestApplicationManager {
      initTestApplication().getOrThrow()
      return ourInstance
    }

    @JvmStatic
    fun getInstanceIfCreated(): TestApplicationManager? {
      if (isApplicationInitialized) {
        return ourInstance
      }
      else {
        return null
      }
    }

    private var testCounter = 0

    @ApiStatus.Internal
    @TestOnly
    @JvmStatic
    fun tearDownProjectAndApp(project: Project) {
      if (project.isDisposed) {
        return
      }

      val isLightProject = ProjectManagerImpl.isLight(project)
      val app = ApplicationManager.getApplication()

      com.intellij.testFramework.common.runAll(
        {
          if (isLightProject) {
            project.serviceIfCreated<AutoPopupController>()?.cancelAllRequests()
          }
        },
        { CodeStyle.dropTemporarySettings(project) },
        { WriteIntentReadAction.run<Nothing?> { UsefulTestCase.doPostponedFormatting(project) } },
        { LookupManager.hideActiveLookup(project) },
        {
          if (isLightProject) {
            (project.serviceIfCreated<StartupManager>() as StartupManagerImpl?)?.prepareForNextTest()
          }
        },
        {
          if (isLightProject) {
            LightPlatformTestCase.tearDownSourceRoot(project)
          }
        },
        {
          app.runWriteIntentReadAction<Unit, Nothing?> {
            WriteCommandAction.runWriteCommandAction(project) {
              app.serviceIfCreated<FileDocumentManager, FileDocumentManagerImpl>()?.dropAllUnsavedDocuments()
            }
          }
        },
        { project.serviceIfCreated<EditorHistoryManager>()?.removeAllFiles() },
        {
          if (project.serviceIfCreated<PsiManager>()?.isDisposed == true) {
            throw IllegalStateException("PsiManager must be not disposed")
          }
        },
        { LightPlatformTestCase.checkAssertions() },
        { LightPlatformTestCase.clearUncommittedDocuments(project) },
        { app.runWriteIntentReadAction<Unit, Nothing?> { (UndoManager.getInstance(project) as UndoManagerImpl).dropHistoryInTests() } },
        { project.serviceIfCreated<TemplateDataLanguageMappings>()?.cleanupForNextTest() },
        { (project.serviceIfCreated<PsiManager>() as PsiManagerImpl?)?.cleanupForNextTest() },
        { (project.serviceIfCreated<StructureViewFactory>() as StructureViewFactoryImpl?)?.cleanupForNextTest() },
        { waitForProjectLeakingThreads(project) },
        { dropModuleRootCaches(project) },
        {
          // reset data provider before disposing the project to ensure that the disposed project is not accessed
          getInstanceIfCreated()?.setDataProvider(null)
        },
        { WriteIntentReadAction.run { ProjectManagerEx.getInstanceEx().forceCloseProject(project) } },
        {
          if (testCounter++ % 100 == 0) {
            // Some tests are written in Groovy, and running all of them may result in some 40M of memory wasted on bean data,
            // so let's clear the cache occasionally to ensure it doesn't grow too big.
            GCUtil.clearBeanInfoCache()
          }
        },
        { app.cleanApplicationStateCatching()?.let { throw it } }
      )
    }

    private inline fun <reified T : Any, reified TI : Any> Application.serviceIfCreated(): TI? {
      return this.getServiceIfCreated(T::class.java) as? TI
    }

    private fun dropModuleRootCaches(project: Project) {
      WriteAction.runAndWait<RuntimeException> {
        for (module in ModuleManager.getInstance(project).modules) {
          (ModuleRootManager.getInstance(module) as ModuleRootComponentBridge).dropCaches()
        }
      }
    }

    /**
     * Call this method after the test to check whether project instances leak.
     * This is done automatically on CI inside {@code _LastInSuiteTest.testProjectLeak}.
     * However, you may want to add this check to a particular test to make sure 
     * whether it causes the leak or not.
     */
    @JvmStatic
    fun testProjectLeak() {
      if (java.lang.Boolean.getBoolean("idea.test.guimode")) {
        val application = ApplicationManager.getApplication()
        application.invokeAndWait {
          UIUtil.dispatchAllInvocationEvents()
          application.exit(true, true, false)
        }
        ShutDownTracker.getInstance().waitFor(100, TimeUnit.SECONDS)
        return
      }
      disposeApplicationAndCheckForLeaks()
    }

    /**
     * Disposes the application (it also stops some application-related threads) and checks for project leaks.
     */
    @JvmStatic
    fun disposeApplicationAndCheckForLeaks() {
      val edtThrowable = runInEdtAndGet {
        runAllCatching(
          { PlatformTestUtil.cleanupAllProjects() },
          { EDT.dispatchAllInvocationEvents() },
          {
            println((AppExecutorUtil.getAppScheduledExecutorService() as AppScheduledExecutorService).statistics())
            println("ProcessIOExecutorService threads created: ${(ProcessIOExecutorService.INSTANCE as ProcessIOExecutorService).threadCounter}")
          },
          {
            val app = ApplicationManager.getApplication() as? ApplicationImpl
            app?.messageBus?.syncPublisher(AppLifecycleListener.TOPIC)?.appWillBeClosed(false)
          },
          { UsefulTestCase.waitForAppLeakingThreads(10, TimeUnit.SECONDS) },
          {
            if (ApplicationManager.getApplication() != null) {
              assertNonDefaultProjectsAreNotLeaked()
            }
          },
          {
            @Suppress("SSBasedInspection")
            getInstanceIfCreated()?.dispose()
          },
          {
            EDT.dispatchAllInvocationEvents()
          },
        )
      }

      listOfNotNull(edtThrowable, runAllCatching(
        {
          assertDisposerEmpty()
        }
      )).reduceAndThrow()
    }

    @ApiStatus.Internal
    @JvmStatic
    fun waitForProjectLeakingThreads(project: Project) {
      if (project is ComponentManagerImpl) {
        project.stopServicePreloading()
      }

      (project.serviceIfCreated<GeneratedSourceFileChangeTracker>() as GeneratedSourceFileChangeTrackerImpl?)
        ?.cancelAllAndWait(10, TimeUnit.SECONDS)
    }

    @ApiStatus.ScheduledForRemoval
    @Deprecated(
      message = "moved to dump.kt",
      replaceWith = ReplaceWith("com.intellij.testFramework.common.publishHeapDump(fileNamePrefix)")
    )
    @JvmStatic
    fun publishHeapDump(fileNamePrefix: String): String {
      return com.intellij.testFramework.common.publishHeapDump(fileNamePrefix)
    }
  }

  fun setDataProvider(provider: DataProvider?) {
    dataManager.setTestDataProvider(provider)
  }

  fun setDataProvider(provider: DataProvider?, parentDisposable: Disposable?) {
    dataManager.setTestDataProvider(provider, parentDisposable!!)
  }

  fun getData(dataId: String): Any? = dataManager.dataContext.getData(dataId)

  fun dispose() {
    disposeTestApplication()
  }
}
