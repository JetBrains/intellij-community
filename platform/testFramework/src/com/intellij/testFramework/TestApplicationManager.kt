// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.*
import com.intellij.ide.impl.HeadlessDataManager
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
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.common.*
import com.intellij.ui.UiInterceptors
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.ref.GCUtil
import com.intellij.util.throwIfNotEmpty
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
      initTestApplication()
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

      // reset data provider before disposing the project to ensure that the disposed project is not accessed
      l.catchAndStoreExceptions { getInstanceIfCreated()?.setDataProvider(null) }
      l.catchAndStoreExceptions { ProjectManagerEx.getInstanceEx().forceCloseProject(project) }
      l.catchAndStoreExceptions { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }

      l.catchAndStoreExceptions { UiInterceptors.clear() }
      l.catchAndStoreExceptions { CompletionProgressIndicator.cleanupForNextTest() }
      l.catchAndStoreExceptions {
        if (testCounter++ % 100 == 0) {
          // Some tests are written in Groovy, and running all of them may result in some 40M of memory wasted on bean data,
          // so let's clear the cache occasionally to ensure it doesn't grow too big.
          GCUtil.clearBeanInfoCache()
        }
      }

      throwIfNotEmpty(l)
    }

    private inline fun <reified T : Any, reified TI : Any> Application.serviceIfCreated(): TI? =
      this.getServiceIfCreated(T::class.java) as? TI

    private fun dropModuleRootCaches(project: Project) {
      WriteAction.runAndWait<RuntimeException> {
        for (module in ModuleManager.getInstance(project).modules) {
          (ModuleRootManager.getInstance(module) as ModuleRootComponentBridge).dropCaches()
        }
      }
    }

    /**
     * Disposes the application (it also stops some application-related threads) and checks for project leaks.
     */
    @JvmStatic
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
          if (ApplicationManager.getApplication() != null) {
            assertNonDefaultProjectsAreNotLeaked()
          }
        }

        l.catchAndStoreExceptions {
          @Suppress("SSBasedInspection")
          getInstanceIfCreated()?.dispose()
        }

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

    @ApiStatus.Internal
    @JvmStatic
    fun waitForProjectLeakingThreads(project: Project) {
      if (project is ComponentManagerImpl) {
        project.stopServicePreloading()
      }

      (project.serviceIfCreated<GeneratedSourceFileChangeTracker>() as GeneratedSourceFileChangeTrackerImpl?)
        ?.cancelAllAndWait(10, TimeUnit.SECONDS)
    }

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

  fun getData(dataId: String) = dataManager.dataContext.getData(dataId)

  fun dispose() {
    disposeTestApplication()
  }
}
