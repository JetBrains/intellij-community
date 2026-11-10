// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorProviderBean
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.testFramework.junit5.highlighting.fixture.awaitHighlighting
import com.intellij.testFramework.junit5.highlighting.fixture.highlightingFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Suppress("SuspiciousPackagePrivateAccess")
@TestApplication
class DaemonCodeAnalyzerTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
    val project2 = projectFixture(openAfterCreation = true)
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val file = sourceRoot.psiFileFixture("A.txt", "text")
    val nonAwtFile = sourceRoot.virtualFileFixture("NonAwt.txt", "text")
  }

  private val localEditor: TestFixture<Editor> = file.editorFixture()

  private val highlighting: TestFixture<DaemonCodeAnalyzer> = localEditor.highlightingFixture()

  private val wrapperProject: TestFixture<Project> = projectFixture()
  private val wrapperFileEditorManager: TestFixture<FileEditorManagerImpl> = wrapperProject.fileEditorManagerFixture()

  @Test
  fun `non-AWT document change does not acquire daemon read lock`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    project.get()
    project2.get() // init two projects to trigger complex logic of project guessing
    highlighting.get() // init listeners
    val virtualFile = LightVirtualFile("test.txt", "text")
    virtualFile.putUserData(AbstractFileViewProvider.FREE_THREADED, true)
    val document = assertIs<DocumentImpl>(readAction { FileDocumentManager.getInstance().getDocument(virtualFile) })
    assertFalse(document.isWriteThreadOnly)
    DaemonCodeAnalyzer.getInstance(project.get())
    runReadActionBlocking {
      PsiDocumentManager.getInstance(project.get()).getPsiFile(document) // initialize viewprovider to avoid write action in listeners
    }
    withContext(Dispatchers.UiWithModelAccess) {
      ApplicationManagerEx.getApplicationEx().withLocksSoftlyProhibited(
        "Daemon document listener must not acquire locks for a non-AWT document change",
        { throw it },
      ) {
        document.setText("txet")
      }
    }
  }

  /**
   * An editor added to a hierarchy on `Dispatchers.UI` reaches `EditorTrackerImpl.updateLocalActiveEditors` from a Swing `ancestor` event.
   * A headless test cannot create a `Window`, so it reaches the same path through `setActiveEditorsInTests`.
   */
  @Test
  fun `active editors change on Dispatchers UI takes no lock`(): Unit = timeoutRunBlocking {
    highlighting.get() // init DaemonListeners, the platform subscriber
    val editor = localEditor.get()
    val tracker = EditorTracker.getInstance(project.get())
    withContext(Dispatchers.UI) {
      tracker.setActiveEditorsInTests(listOf(editor))
      tracker.setActiveEditorsInTests(emptyList())
    }
  }

  @Test
  fun `external annotator doAnnotate runs outside non-cancellable section`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val doAnnotateSection = CompletableDeferred<Boolean>()
    val applied = CompletableDeferred<Unit>()
    val annotator = object : ExternalAnnotator<Unit, Unit>() {
      override fun collectInformation(psiFile: PsiFile) = Unit

      override fun doAnnotate(collectedInfo: Unit?) {
        doAnnotateSection.complete(Cancellation.isInNonCancelableSection())
      }

      override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        applied.complete(Unit)
      }
    }
    ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, annotator, disposable)

    withContext(Dispatchers.EDT) {
      highlighting.get().restart("external annotator doAnnotate cancellation test")
    }

    val wasInNonCancelableSection = doAnnotateSection.await()
    applied.await()
    localEditor.get().awaitHighlighting()

    assertFalse(wasInNonCancelableSection)
  }

  @Test
  fun `non-highlightable editor does not restart daemon`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val virtualFile = nonAwtFile.get()
    FileEditorProviderBean.EP_NAME.point.registerExtension(FileEditorProviderBean(object : FileEditorProvider, DumbAware {
      override fun accept(project: Project, file: VirtualFile): Boolean = file == virtualFile

      override fun createEditor(project: Project, file: VirtualFile): FileEditor = NonHighlightableFileEditor(file)

      override fun getEditorTypeId(): String = "non-highlightable-editor"

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }), disposable)

    val analyzer = assertIs<DaemonCodeAnalyzerImpl>(DaemonCodeAnalyzer.getInstance(project.get()))
    val fileEditorManager = FileEditorManager.getInstance(project.get())
    withContext(Dispatchers.EDT) {
      fileEditorManager.openFile(virtualFile, true)
      analyzer.restart("Non-highlightable editor opened")
    }
    try {
      waitUntil("the daemon must stop after it skips the non-highlightable editor") {
        withContext(Dispatchers.EDT) {
          !analyzer.isRunningOrPending
        }
      }
    }
    finally {
      withContext(Dispatchers.EDT) {
        fileEditorManager.closeFile(virtualFile)
        EditorHistoryManager.getInstance(project.get()).removeFile(virtualFile)
      }
    }
  }

  @Test
  fun `text editor wrapper keeps the highlighting editor on focus gain`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val virtualFile = LightVirtualFile("wrapped.txt", "text")
    FileEditorProviderBean.EP_NAME.point.registerExtension(FileEditorProviderBean(object : FileEditorProvider, DumbAware {
      override fun accept(project: Project, file: VirtualFile): Boolean = file == virtualFile

      override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val textEditor = assertIs<TextEditor>(TextEditorProvider.getInstance().createEditor(project, file))
        return TextEditorWithPreview(textEditor, NonHighlightableFileEditor(file))
      }

      override fun getEditorTypeId(): String = "wrapped-text-editor"

      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }), disposable)

    val fileEditorManager = wrapperFileEditorManager.get()
    val analyzer = assertIs<DaemonCodeAnalyzerImpl>(DaemonCodeAnalyzer.getInstance(wrapperProject.get()))
    val tracker = EditorTracker.getInstance(wrapperProject.get())
    val composite = fileEditorManager.openFile(virtualFile, FileEditorOpenRequest.defaults())
    withContext(Dispatchers.EDT) {
      try {
        val wrapper = assertIs<TextEditorWithPreview>(composite.allEditors.single())
        tracker.setActiveEditorsInTests(emptyList())
        assertSame(wrapper.textEditor, analyzer.selectedEditors.single())

        tracker.setActiveEditorsInTests(listOf(wrapper.editor))
        assertSame(wrapper.textEditor, analyzer.selectedEditors.single())
      }
      finally {
        fileEditorManager.closeFile(virtualFile)
      }
    }
  }

  /**
   * A request of the file status queue waits for the read lock behind a pending background `edtWriteAction`.
   * The write action needs the EDT, so the EDT must dispatch events while it waits for the queue.
   */
  @Test
  fun `file status queue wait lets a pending edtWriteAction finish`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    highlighting.get() // init DaemonListeners
    val analyzer = assertIs<DaemonCodeAnalyzerImpl>(DaemonCodeAnalyzer.getInstance(project.get()))
    val writeBlock = { }
    val writePending = CompletableDeferred<Unit>()
    (ApplicationManager.getApplication() as ApplicationEx).addWriteActionListener(object : WriteActionListener {
      override fun beforeWriteActionStart(action: Class<*>) {
        if (action == writeBlock.javaClass) writePending.complete(Unit)
      }
    }, disposable)
    val readActionRan = AtomicBoolean(false)
    withContext(Dispatchers.EDT) {
      analyzer.myListeners.runAfterUpdateFileStatusQueue {
        runBlockingMaybeCancellable {
          // The bound makes a deadlock fail the test instead of a hang of the EDT.
          withTimeoutOrNull(5.seconds) {
            writePending.await()
            readAction { readActionRan.set(true) }
          }
        }
      }
      launch(Dispatchers.Default) {
        edtWriteAction(writeBlock)
      }
      TestDaemonCodeAnalyzerImpl(project.get()).waitForUpdateFileStatusBackgroundQueueInTests()
    }
    assertTrue(writePending.isCompleted, "The edtWriteAction must become pending")
    assertTrue(readActionRan.get(), "The queue request must get the read lock after the edtWriteAction")
  }

  @Test
  fun `highlighting gets canceled on pending write action`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val analyzer = highlighting.get()
    val listenerInvoked = AtomicBoolean(false)
    project.get().messageBus.connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object: DaemonCodeAnalyzer.DaemonListener {
      override fun daemonStarting(fileEditors: Collection<FileEditor>) {
        listenerInvoked.set(true)
      }
    })
    launch(Dispatchers.Default) {
      backgroundWriteAction {  }
    }
    while (!ApplicationManagerEx.getApplicationEx().isWriteActionPending) {
      Thread.sleep(10)
    }
    analyzer.restart(this)
    Thread.sleep(DaemonCodeAnalyzerSettings.getInstance().autoReparseDelay.times(2).toLong())
    UIUtil.dispatchAllInvocationEvents()
    assertFalse(listenerInvoked.get())
  }
}

private class NonHighlightableFileEditor(private val virtualFile: VirtualFile) : UserDataHolderBase(), FileEditor {
  private val component: JPanel = JPanel()

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): String = "Non-Highlightable Editor"

  override fun setState(state: FileEditorState) { }

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = virtualFile.isValid

  override fun addPropertyChangeListener(listener: PropertyChangeListener) { }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) { }

  override fun getFile(): VirtualFile = virtualFile

  override fun dispose() { }
}
