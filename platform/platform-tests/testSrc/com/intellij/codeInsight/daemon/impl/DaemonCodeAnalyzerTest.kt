// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.progress.Cancellation
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.editorFixture
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
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertIs

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

  private val localEditor = file.editorFixture()

  private val highlighting = localEditor.highlightingFixture()

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
