// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.CompositeTabIconHolderCreator
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.BackgroundTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.TaskSupport
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabInfoIconHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.Icon

@TestApplication
internal class EditorCompositeInitializationTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val managerFixture = projectFixture.fileEditorManagerFixture()

  @Test
  fun headlessInitializationUsesDisabledProgress(): Unit = headlessInitializationUsesOptionsSetAfterConstruction(false)

  @Test
  fun headlessInitializationUsesEnabledProgress(): Unit = headlessInitializationUsesOptionsSetAfterConstruction(true)

  private fun headlessInitializationUsesOptionsSetAfterConstruction(showProgress: Boolean): Unit = timeoutRunBlocking {
    assertThat(ApplicationManager.getApplication().isHeadlessEnvironment).isTrue()
    managerFixture.get()
    val file = LightVirtualFile("headless-initialization.txt", PlainTextFileType.INSTANCE, "text")
    val progressStarted = capturePreparation(file)
    val collectionStarted = CompletableDeferred<Unit>()
    val scope = childScope("Headless initialization options") + Dispatchers.Default
    val composite = withContext(Dispatchers.EDT) {
      EditorComposite(file, flow {
        collectionStarted.complete(Unit)
        awaitCancellation()
      }, projectFixture.get(), scope).also {
        Disposer.register(disposable, it)
        it.preparationVisibleInStatusBar = false
        it.showPreparationProgress = showProgress
      }
    }
    try {
      collectionStarted.await()
      if (showProgress) {
        progressStarted.await()
      }
      else {
        assertThat(progressStarted.isCompleted).isFalse()
      }
    }
    finally {
      withContext(Dispatchers.EDT) { Disposer.dispose(composite) }
    }
  }

  @Test
  fun documentPreparationStartsBeforeEnabledProgress(): Unit = documentPreparationStartsBeforeInitialization(true)

  @Test
  fun documentPreparationStartsBeforeDisabledProgress(): Unit = documentPreparationStartsBeforeInitialization(false)

  private fun documentPreparationStartsBeforeInitialization(showProgress: Boolean): Unit = timeoutRunBlocking {
    managerFixture.get()
    val file = LightVirtualFile("early-document.txt", PlainTextFileType.INSTANCE, "text")
    val progressStarted = capturePreparation(file)
    val documentStarted = CompletableFuture<Unit>()
    val collectionStarted = CompletableDeferred<Unit>()
    val scope = childScope("Early document initialization") + Dispatchers.Default
    val preparation = EditorDocumentPreparation(scope, file) {
      documentStarted.complete(Unit)
      null
    }
    val model = DocumentPreparedEditorModel(flow {
      preparation.await()
      collectionStarted.complete(Unit)
      awaitCancellation()
    }, preparation)
    val composite = withContext(Dispatchers.EDT) {
      EditorComposite(file, model, projectFixture.get(), scope).also {
        Disposer.register(disposable, it)
        it.preparationVisibleInStatusBar = false
        it.showPreparationProgress = showProgress
        preparation.start()
        documentStarted.get(5, TimeUnit.SECONDS)
        assertThat(collectionStarted.isCompleted).isFalse()
        assertThat(progressStarted.isCompleted).isFalse()
      }
    }
    try {
      collectionStarted.await()
      if (showProgress) {
        progressStarted.await()
      }
      else {
        assertThat(progressStarted.isCompleted).isFalse()
      }
    }
    finally {
      withContext(Dispatchers.EDT) { Disposer.dispose(composite) }
    }
  }

  @Test
  fun failedMountCancelsDocumentPreparationAndPendingOpen(): Unit = timeoutRunBlocking {
    val manager = managerFixture.get() as FileEditorManagerImpl
    val file = LightVirtualFile("failed-mount.txt", PlainTextFileType.INSTANCE, "text")
    var failedComposite: EditorComposite? = null
    ApplicationManager.getApplication().replaceService(
      CompositeTabIconHolderCreator::class.java,
      object : CompositeTabIconHolderCreator {
        override fun createTabIconHolder(
          composite: EditorComposite,
          owner: TabInfo,
          startupIcon: Icon?,
          waitForEditors: Boolean,
        ): TabInfoIconHolder {
          failedComposite = composite
          error("tab mount failed")
        }
      },
      disposable,
    )

    val result = runCatching { manager.openFile(file, FileEditorOpenOptions()) }
    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java).hasMessage("tab mount failed")
    val composite = checkNotNull(failedComposite)
    composite.availableJob.join()
    EditorOpenTracker.getInstance(projectFixture.get()).pendingEditorOpen().join()
    withContext(Dispatchers.EDT) {
      assertThat(composite.isDisposed()).isTrue()
      assertThat(Disposer.isDisposed(composite)).isTrue()
      assertThat(manager.isFileOpen(file)).isFalse()
    }
  }

  private fun capturePreparation(file: LightVirtualFile): CompletableDeferred<Unit> {
    val started = CompletableDeferred<Unit>()
    val application = ApplicationManager.getApplication()
    val delegate = application.service<TaskSupport>()
    application.replaceService(TaskSupport::class.java, object : TaskSupport by delegate {
      override suspend fun <T> withBackgroundProgressInternal(
        owner: BackgroundTaskOwner,
        title: @NlsContexts.ProgressTitle String,
        cancellation: TaskCancellation,
        suspender: TaskSuspender?,
        visibleInStatusBar: Boolean,
        action: suspend CoroutineScope.() -> T,
      ): T {
        if (title != IdeBundle.message("progress.title.preparing.file", file.name)) {
          return delegate.withBackgroundProgressInternal(owner, title, cancellation, suspender, visibleInStatusBar, action)
        }
        assertThat(visibleInStatusBar).isFalse()
        started.complete(Unit)
        return coroutineScope { action() }
      }
    }, disposable)
    return started
  }
}
