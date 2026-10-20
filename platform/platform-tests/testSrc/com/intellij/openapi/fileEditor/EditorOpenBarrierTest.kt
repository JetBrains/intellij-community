// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.util.EditorHelper
import com.intellij.ide.util.requestOpenFilesInEditor
import com.intellij.ide.util.requestOpenInEditor
import com.intellij.mock.Mock
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.EditorOpenTracker
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.awaitPendingEditorOpen
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.mock
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.io.path.writeText

@TestApplication
@Timeout(30)
class EditorOpenBarrierTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()
  private val tempDirFixture = tempPathFixture()

  private val project: Project
    get() = projectFixture.get()

  private val manager: FileEditorManagerImpl
    get() = fileEditorManagerFixture.get()

  private val apiManager: FileEditorManager
    get() = manager

  @BeforeEach
  fun clearEditorStateBeforeTest(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    manager.closeAllFiles()
    EditorHistoryManager.getInstance(project).removeAllFiles()
    (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()
  }

  @AfterEach
  fun clearEditorStateAfterTest(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    manager.closeAllFiles()
    EditorHistoryManager.getInstance(project).removeAllFiles()
  }

  @Test
  fun `barrier is a snapshot`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val tracker = EditorOpenTracker.getInstance(project)
    val first = Job().also { tracker.track(it) }
    val snapshotBarrier = tracker.pendingEditorOpen()
    // submitted after the snapshot, so it is not a part of it
    val second = Job().also { tracker.track(it) }

    first.complete()
    assertThat(snapshotBarrier.isCompleted).isTrue()
    assertThat(tracker.pendingEditorOpen().isCompleted).isFalse()

    // cancellation counts as completion
    second.cancel()
    assertThat(tracker.pendingEditorOpen().isCompleted).isTrue()
  }

  enum class SuspendingOpen { FILE, EDITOR, TEXT_EDITOR }

  enum class RequestOpen { FILE, FILE_WITH_REQUEST, EDITOR, TEXT_EDITOR }

  @ParameterizedTest
  @EnumSource(RequestOpen::class)
  fun `canceling a queued request prevents opening and releases the barrier`(api: RequestOpen): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val file = createFile("cancel-queued.txt")
      val future = requestOpen(file, api)
      assertThat(manager.isFileOpen(file)).isFalse()

      future.cancel(false)
      project.awaitPendingEditorOpen()

      assertThat(future.isCancelled).isTrue()
      assertThat(manager.isFileOpen(file)).isFalse()
    }

  @ParameterizedTest
  @EnumSource(RequestOpen::class)
  fun `canceling a request keeps the tab and its initialization`(api: RequestOpen): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val file = createFile("cancel-created.txt")
      val creationStarted = CompletableDeferred<Unit>()
      val proceed = CompletableDeferred<Unit>()
      var navigationCalls = 0
      registerGatedProvider("cancel-request", file, creationStarted, proceed) { navigationCalls++ }
      val future = requestOpen(file, api)
      creationStarted.await()
      assertThat(future.isDone).isFalse()

      future.cancel(false)
      proceed.complete(Unit)
      project.awaitPendingEditorOpen()

      assertThat(future.isCancelled).isTrue()
      assertThat(manager.getEditors(file)).isNotEmpty()
      assertThat(navigationCalls).isZero()
    }

  @ParameterizedTest
  @EnumSource(RequestOpen::class)
  fun `closing a tab cancels its pending request`(api: RequestOpen): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val file = createFile("close-pending.txt")
      val creationStarted = CompletableDeferred<Unit>()
      registerGatedProvider("close-request", file, creationStarted, CompletableDeferred())
      val future = requestOpen(file, api)
      creationStarted.await()
      assertThat(future.isDone).isFalse()

      manager.closeFile(file)
      project.awaitPendingEditorOpen()

      assertThat(future.isCancelled).isTrue()
      assertThat(manager.isFileOpen(file)).isFalse()
    }

  private fun requestOpen(file: VirtualFile, api: RequestOpen): CompletableFuture<*> {
    val descriptor = OpenFileDescriptor(project, file)
    return when (api) {
      RequestOpen.FILE -> apiManager.requestOpenFile(file)
      RequestOpen.FILE_WITH_REQUEST -> apiManager.requestOpenFile(file, FileEditorOpenRequest.defaults())
      RequestOpen.EDITOR -> apiManager.requestOpenEditor(descriptor, FileEditorOpenRequest.defaults())
      RequestOpen.TEXT_EDITOR -> apiManager.requestOpenTextEditor(descriptor, FileEditorOpenRequest.defaults())
    }
  }

  @ParameterizedTest
  @EnumSource(RequestOpen::class)
  fun `background request is tracked before EDT dispatch and ignores its gesture`(api: RequestOpen): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      apiManager.requestOpenFile(createFile("initial.txt")).await()
      val window = checkNotNull(manager.currentWindow)
      val file = createFile("background.txt")
      val descriptor = OpenFileDescriptor(project, file)
      val tracker = EditorOpenTracker.getInstance(project)
      lateinit var future: CompletableFuture<*>
      dispatchOpenInRightSplitGesture(disposable) {
        future = CompletableFuture.supplyAsync {
          ApplicationManager.getApplication().assertIsNonDispatchThread()
          when (api) {
            RequestOpen.FILE -> apiManager.requestOpenFile(file)
            RequestOpen.FILE_WITH_REQUEST -> apiManager.requestOpenFile(file, FileEditorOpenRequest.defaults())
            RequestOpen.EDITOR -> apiManager.requestOpenEditor(descriptor, FileEditorOpenRequest.defaults())
            RequestOpen.TEXT_EDITOR -> apiManager.requestOpenTextEditor(descriptor, FileEditorOpenRequest.defaults())
          }
        }.get(10, TimeUnit.SECONDS)
        assertThat(future.isDone).isFalse()
        assertThat(manager.getComposite(file)).isNull()
        assertThat(tracker.pendingEditorOpen().isCompleted).isFalse()
      }
      val callback = future.thenAccept { ApplicationManager.getApplication().assertIsDispatchThread() }
      tracker.pendingEditorOpen().join()
      callback.await()
      assertThat(future.isDone).isTrue()
      assertThat(window.isFileOpen(file)).isTrue()
      assertThat(manager.windows).hasSize(1)
    }

  @ParameterizedTest
  @EnumSource(SuspendingOpen::class)
  fun `suspending open is tracked before composite creation and not after return`(api: SuspendingOpen): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val file = createFile("suspending.txt")
      val opened = CompletableDeferred<Unit>()
      val finishCaller = CompletableDeferred<Unit>()
      val caller = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        openSuspending(file, api)
        opened.complete(Unit)
        finishCaller.await()
      }
      try {
        assertThat(manager.getComposite(file)).isNull()
        val barrier = EditorOpenTracker.getInstance(project).pendingEditorOpen()
        assertThat(barrier.isCompleted).isFalse()

        opened.await()
        barrier.join()
        assertThat(manager.getEditors(file)).isNotEmpty()
        assertThat(caller.isActive).isTrue()
        assertThat(EditorOpenTracker.getInstance(project).pendingEditorOpen().isCompleted).isTrue()
      }
      finally {
        finishCaller.complete(Unit)
        caller.cancelAndJoin()
      }
    }

  @Test
  fun `canceling a suspending open releases its barrier`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val file = createFile("cancel-suspending.txt")
      val caller = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        apiManager.openTextEditor(OpenFileDescriptor(project, file), FileEditorOpenRequest.defaults())
      }
      val barrier = EditorOpenTracker.getInstance(project).pendingEditorOpen()
      assertThat(barrier.isCompleted).isFalse()
      caller.cancelAndJoin()
      barrier.join()
      assertThat(caller.isCancelled).isTrue()
      assertThat(EditorOpenTracker.getInstance(project).pendingEditorOpen().isCompleted).isTrue()
    }

  private suspend fun openSuspending(file: VirtualFile, api: SuspendingOpen) {
    when (api) {
      SuspendingOpen.FILE -> apiManager.openFile(file, FileEditorOpenRequest.defaults())
      SuspendingOpen.EDITOR -> apiManager.openEditor(OpenFileDescriptor(project, file), FileEditorOpenRequest.defaults())
      SuspendingOpen.TEXT_EDITOR -> apiManager.openTextEditor(OpenFileDescriptor(project, file), FileEditorOpenRequest.defaults())
    }
  }

  @Test
  fun `existing editor navigation is tracked and a failure releases the barrier`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val file = createFile("navigation-failure.txt")
      val proceed = CompletableDeferred<Unit>().apply { complete(Unit) }
      var navigationCalls = 0
      registerGatedProvider("navigation-failure", file, CompletableDeferred(), proceed) {
        assertThat(EditorOpenTracker.getInstance(project).pendingEditorOpen().isCompleted).isFalse()
        navigationCalls++
        throw IllegalStateException("navigation failed")
      }
      apiManager.openFile(file, FileEditorOpenRequest.defaults())
      project.awaitPendingEditorOpen()

      try {
        apiManager.openEditor(OpenFileDescriptor(project, file), FileEditorOpenRequest.defaults())
        throw AssertionError("The navigation failure must reach the caller")
      }
      catch (failure: IllegalStateException) {
        assertThat(failure).hasMessage("navigation failed")
      }
      assertThat(navigationCalls).isEqualTo(1)
      assertThat(EditorOpenTracker.getInstance(project).pendingEditorOpen().isCompleted).isTrue()
    }

  @Test
  fun `bridge open is awaited by the barrier`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile("1.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceedWithCreation = CompletableDeferred<Unit>()
    registerGatedProvider("barrier-bridge", file, creationStarted, proceedWithCreation)

    val psi = psiFile(file)
    val openedEditor = readAction { requestOpenInEditor(psi, requestFocus = false) }
    val callback = openedEditor.thenAccept {
      ApplicationManager.getApplication().assertIsDispatchThread()
      assertThat(EditorOpenTracker.getInstance(project).pendingEditorOpen().isCompleted).isFalse()
      ReadAction.runBlocking<RuntimeException> { ApplicationManager.getApplication().assertReadAccessAllowed() }
    }

    // the provider gate holds the open back; without the barrier an assertion on the open editor would fail here
    creationStarted.await()
    assertThat(EditorOpenTracker.getInstance(project).pendingEditorOpen().isCompleted).isFalse()
    assertThat(openedEditor.isDone).isFalse()

    proceedWithCreation.complete(Unit)
    project.awaitPendingEditorOpen()

    assertThat(manager.getEditors(file)).isNotEmpty()
    assertThat(openedEditor.isDone).isTrue()
    assertThat(openedEditor.await()).isSameAs((manager.getSelectedEditor(file) as TextEditor).editor)
    callback.await()
  }

  @Test
  fun `bridge submitted under a write action is awaited by the barrier`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile("1.txt")
    val psi = psiFile(file)

    WriteCommandAction.runWriteCommandAction(project) {
      requestOpenInEditor(psi, false)
    }
    project.awaitPendingEditorOpen()

    assertThat(manager.isFileOpen(file)).isTrue()
  }

  @Test
  fun `bridge future contains the navigation failure`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val failure = IllegalStateException("navigation failed")
    val file = createFile("bridge-failure.txt")
    registerGatedProvider("bridge-failure", file, CompletableDeferred(), CompletableDeferred(Unit)) { throw failure }
    apiManager.openFile(file, FileEditorOpenRequest.defaults())
    project.awaitPendingEditorOpen()

    val logged = CompletableDeferred<Throwable>()
    LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        if (t is IllegalStateException && t.message == failure.message) {
          logged.complete(t)
          return Action.NONE
        }
        return super.processError(category, message, details, t)
      }
    }).use {
      val psi = psiFile(file)
      val request = readAction { requestOpenInEditor(psi, requestFocus = false) }
      project.awaitPendingEditorOpen()
      assertThat(request.isCompletedExceptionally).isTrue()
      val cause = request.handle { _, cause -> cause }.await()
      assertThat(cause).isInstanceOf(IllegalStateException::class.java).hasMessage(failure.message)
      assertThat(logged.await()).isSameAs(cause)
    }
  }

  @Test
  fun `cancellation during navigation cancels the bridge future`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile("bridge-cancellation.txt")
    registerGatedProvider("bridge-cancellation", file, CompletableDeferred(), CompletableDeferred(Unit)) {
      throw CancellationException("navigation canceled")
    }
    apiManager.openFile(file, FileEditorOpenRequest.defaults())
    project.awaitPendingEditorOpen()

    val psi = psiFile(file)
    val request = readAction { requestOpenInEditor(psi, requestFocus = false) }
    project.awaitPendingEditorOpen()
    assertThat(request.isCancelled).isTrue()
  }

  enum class HelperApi { JAVA_TEXT, KOTLIN_FILE, JAVA_FILE }

  @Test
  fun `helpers skip elements without a target before submitting work`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val invalidElement = mock(PsiElement::class.java)
      val tracker = EditorOpenTracker.getInstance(project)
      val futures = readAction {
        listOf(
          requestOpenInEditor(invalidElement, requestFocus = false),
          requestOpenInEditor(invalidElement, switchToText = false, requestFocus = false),
          requestOpenFilesInEditor(arrayOf(invalidElement)),
        )
      }
      assertThat(futures).allSatisfy {
        assertThat(it.isDone).isTrue()
        assertThat(it.getNow(null)).isNull()
      }
      assertThat(tracker.pendingEditorOpen().isCompleted).isTrue()
    }

  @ParameterizedTest
  @EnumSource(HelperApi::class)
  fun `bridge future returns the requested editor type`(api: HelperApi): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile("bridge-result.txt")
    val psi = psiFile(file)
    val future: CompletableFuture<*> = readAction {
      when (api) {
        HelperApi.JAVA_TEXT -> EditorHelper.requestOpenInEditor(psi)
        HelperApi.KOTLIN_FILE -> requestOpenInEditor(psi, switchToText = false, requestFocus = false)
        HelperApi.JAVA_FILE -> EditorHelper.requestOpenInEditor(psi, false, false)
      }
    }
    val result = future.await()
    val selected = manager.getSelectedEditor(file)
    val expected = when (api) {
      HelperApi.JAVA_TEXT -> (selected as TextEditor).editor
      HelperApi.KOTLIN_FILE, HelperApi.JAVA_FILE -> selected
    }
    assertThat(result).isNotNull().isSameAs(expected)
  }

  @Test
  fun `canceling a batch stops the remaining requests`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val first = createFile("batch-first.txt")
    val second = createFile("batch-second.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceed = CompletableDeferred<Unit>()
    registerGatedProvider("batch-cancel", first, creationStarted, proceed)
    val elements = arrayOf(psiFile(first), psiFile(second))
    val request = readAction { requestOpenFilesInEditor(elements) }
    creationStarted.await()
    assertThat(request.isDone).isFalse()

    request.cancel(false)
    proceed.complete(Unit)
    project.awaitPendingEditorOpen()

    assertThat(request.isCancelled).isTrue()
    assertThat(manager.isFileOpen(first)).isTrue()
    assertThat(manager.isFileOpen(second)).isFalse()
  }

  @Test
  fun `batch future completes after all files open`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val first = createFile("batch-first.txt")
    val second = createFile("batch-second.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceed = CompletableDeferred<Unit>()
    registerGatedProvider("batch-complete", second, creationStarted, proceed)
    val elements = arrayOf(psiFile(first), psiFile(second))
    val request = readAction { EditorHelper.requestOpenFilesInEditor(elements) }
    creationStarted.await()
    assertThat(manager.isFileOpen(first)).isTrue()
    assertThat(request.isDone).isFalse()

    proceed.complete(Unit)
    assertThat(request.await()).isNull()
    assertThat(manager.isFileOpen(second)).isTrue()
  }

  @Test
  fun `request future is awaited by the barrier`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile("1.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceedWithCreation = CompletableDeferred<Unit>()
    registerGatedProvider("barrier-request", file, creationStarted, proceedWithCreation)

    val completion = apiManager.requestOpenFile(file, FileEditorOpenRequest.defaults())
    creationStarted.await()
    assertThat(completion.isDone).isFalse()
    assertThat(EditorOpenTracker.getInstance(project).pendingEditorOpen().isCompleted).isFalse()

    proceedWithCreation.complete(Unit)
    project.awaitPendingEditorOpen()

    assertThat(completion.isDone).isTrue()
    assertThat(manager.getEditors(file)).isNotEmpty()
  }

  @Test
  fun `composite opened without the request API is awaited by the barrier`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile("1.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceedWithCreation = CompletableDeferred<Unit>()
    registerGatedProvider("barrier-composite", file, creationStarted, proceedWithCreation)

    // a window must exist before the non-waiting open below
    manager.openFile(createFile("2.txt"), false)
    val window = checkNotNull(manager.currentWindow)
    manager.openFileImpl2(window, file, FileEditorOpenOptions(waitForCompositeOpen = false))

    creationStarted.await()
    assertThat(EditorOpenTracker.getInstance(project).pendingEditorOpen().isCompleted).isFalse()

    proceedWithCreation.complete(Unit)
    project.awaitPendingEditorOpen()

    assertThat(manager.getEditors(file)).isNotEmpty()
  }

  @Test
  fun `blocking barrier awaits an open submitted by an open callback`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile("1.txt")
    val file2 = createFile("2.txt")
    val psiFile2 = psiFile(file2)

    val psi = psiFile(file)
    readAction { requestOpenInEditor(psi, requestFocus = false) }.thenAccept {
      ReadAction.runBlocking<RuntimeException> { requestOpenInEditor(psiFile2, false) }
    }
    awaitPendingEditorOpen(project)

    assertThat(manager.isFileOpen(file2)).isTrue()
  }

  private fun registerGatedProvider(
    editorTypeId: String,
    acceptedFile: VirtualFile,
    creationStarted: CompletableDeferred<Unit>,
    proceedWithCreation: CompletableDeferred<Unit>,
    onNavigate: (() -> Unit)? = null,
  ) {
    registerProvider(object : AsyncFileEditorProvider {
      override fun accept(project: Project, fileToAccept: VirtualFile): Boolean = fileToAccept == acceptedFile

      override fun acceptRequiresReadAction(): Boolean = false

      override fun getEditorTypeId(): String = editorTypeId

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

      override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()

      override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
      ): FileEditor {
        creationStarted.complete(Unit)
        proceedWithCreation.await()
        return withContext(Dispatchers.EDT) {
          MyTextEditor(file, checkNotNull(document), editorTypeId, onNavigate)
        }
      }
    })
  }

  private fun registerProvider(provider: FileEditorProvider) {
    FileEditorProviderBean.EP_NAME.point.registerExtension(FileEditorProviderBean(provider), disposable)
  }

  @Test
  fun `canceling a bridge skips its callback and keeps the tab`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile("cancel.txt")
    val creationStarted = CompletableDeferred<Unit>()
    val proceedWithCreation = CompletableDeferred<Unit>()
    registerGatedProvider("cancel-bridge", file, creationStarted, proceedWithCreation)
    var callbackCalled = false
    val psi = psiFile(file)
    val request = readAction { requestOpenInEditor(psi, requestFocus = false) }
    val callback = request.thenAccept { callbackCalled = true }

    creationStarted.await()
    request.cancel(false)
    proceedWithCreation.complete(Unit)
    project.awaitPendingEditorOpen()

    assertThat(manager.getEditors(file)).isNotEmpty()
    assertThat(callbackCalled).isFalse()
    assertThat(request.isCancelled).isTrue()
    assertThat(callback.isCompletedExceptionally).isTrue()
  }

  @Test
  fun `bridge completes while the submitting dialog is modal`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val file = createFile("modal.txt")
    val psiFile = psiFile(file)
    val modalEntity = Any()
    LaterInvocator.enterModal(modalEntity)
    withContext(ModalityState.current().asContextElement()) {
      try {
        val opened = readAction { requestOpenInEditor(psiFile, requestFocus = false) }
        assertThat(opened.await()).isNotNull()
      }
      finally {
        LaterInvocator.leaveModal(modalEntity)
      }
    }
  }

  private fun createFile(name: String): VirtualFile {
    val path = tempDirFixture.get().resolve(name)
    path.writeText("content of $name")
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)) { "Can't find $path" }
  }

  private suspend fun psiFile(file: VirtualFile): PsiFile = readAction {
    checkNotNull(PsiManager.getInstance(project).findFile(file)) { "No PSI file for $file" }
  }

  private class MyTextEditor(
    private val file: VirtualFile,
    document: Document,
    private val name: String,
    private val onNavigate: (() -> Unit)?,
  ) : Mock.MyFileEditor(), TextEditor {
    private val editor: Editor = EditorFactory.getInstance().createEditor(document)

    override fun dispose() {
      try {
        EditorFactory.getInstance().releaseEditor(editor)
      }
      finally {
        super.dispose()
      }
    }

    override fun getComponent(): JComponent = JLabel()

    override fun getName(): String = name

    override fun getEditor(): Editor = editor

    override fun canNavigateTo(navigatable: Navigatable): Boolean = onNavigate != null

    override fun navigateTo(navigatable: Navigatable) {
      onNavigate?.invoke()
    }

    override fun getFile(): VirtualFile = file
  }
}
