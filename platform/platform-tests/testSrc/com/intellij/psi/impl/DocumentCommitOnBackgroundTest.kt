// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileDocumentManagerListenerBackgroundable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.util.application
import com.intellij.util.concurrency.TransferredWriteActionService
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class DocumentCommitOnBackgroundTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
  }

  val file = sourceRoot.psiFileFixture("A.java", "class A {}")

  class Recorder {
    val invoked: AtomicInteger = AtomicInteger()
    val onEdt: AtomicInteger = AtomicInteger()

    fun recordUsage() {
      invoked.incrementAndGet()
      if (EDT.isCurrentThreadEdt()) {
        onEdt.incrementAndGet()
      }
    }
  }

  class RecordingTreeChangeListener() : PsiTreeChangeAdapter() {
    val recorder = Recorder()

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
      recorder.recordUsage()
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
      recorder.recordUsage()
    }
  }

  class RecordingTreeChangePreprocessor() : PsiTreeChangePreprocessor {
    val recorder = Recorder()

    override fun treeChanged(event: PsiTreeChangeEventImpl) {
      recorder.recordUsage()
    }
  }

  class RecordingDocumentTransactionListener : PsiDocumentTransactionListener {
    val recorder = Recorder()

    override fun transactionCompleted(document: Document, psiFile: PsiFile) {
      recorder.recordUsage()
    }

    override fun transactionStarted(document: Document, psiFile: PsiFile) {
      recorder.recordUsage()
    }
  }

  class RecordingDocumentTransactionListenerBackgroundable : PsiDocumentTransactionListenerBackgroundable {
    val recorder = Recorder()

    override fun transactionCompleted(document: Document, psiFile: PsiFile) {
      recorder.recordUsage()
    }

    override fun transactionStarted(document: Document, psiFile: PsiFile) {
      recorder.recordUsage()
    }
  }

  @Test
  fun `psiTreeChangeListener and psiTreeChangePreprocessor can be invoked on background`(@TestDisposable testDisposable: Disposable) {
    Registry.get("document.async.commit.with.coroutines").setValue(true, testDisposable)
    val psiDocumentManager = PsiDocumentManager.getInstance(project.get())
    val psiManager = PsiManagerEx.getInstanceEx(project.get())
    val plainListener = RecordingTreeChangeListener()
    val backgroundableListener = RecordingTreeChangeListener()
    psiManager.addPsiTreeChangeListener(plainListener, testDisposable)
    psiManager.addPsiTreeChangeListenerBackgroundable(backgroundableListener, testDisposable)

    val plainPreprocessor = RecordingTreeChangePreprocessor()
    val backgroundablePreprocessor = RecordingTreeChangePreprocessor()

    psiManager.addTreeChangePreprocessor(plainPreprocessor, testDisposable)
    psiManager.addTreeChangePreprocessorBackgroundable(backgroundablePreprocessor, testDisposable)

    invokeAndWaitIfNeeded {
      val file = file.get()
      val document = psiDocumentManager.getDocument(file)!!
      WriteCommandAction.runWriteCommandAction(project.get()) { document.insertString(0, "def") }
      PlatformTestUtil.waitForAllDocumentsCommitted(10, TimeUnit.SECONDS)
    }

    DumbService.getInstance(project.get()).waitForSmartMode()

    assertTrue(plainListener.recorder.invoked.get() > 0)
    assertTrue(plainListener.recorder.onEdt.get() > 0)
    assertTrue(backgroundableListener.recorder.invoked.get() > 0)
    assertTrue(backgroundableListener.recorder.onEdt.get() < backgroundableListener.recorder.invoked.get())


    assertTrue(plainPreprocessor.recorder.invoked.get() > 0)
    assertTrue(plainPreprocessor.recorder.onEdt.get() > 0)
    assertTrue(backgroundablePreprocessor.recorder.invoked.get() > 0)
    // sometimes psi events get fired when dumb mode is ending, in this case we have usages of this listener on EDT
    assertTrue(backgroundablePreprocessor.recorder.onEdt.get() < backgroundablePreprocessor.recorder.invoked.get())
  }

  @Test
  fun `psiDocumentTransactionTest can be invoked on background`(@TestDisposable testDisposable: Disposable) {
    Registry.get("document.async.commit.with.coroutines").setValue(true, testDisposable)
    val psiDocumentManager = PsiDocumentManager.getInstance(project.get())
    val plainListener = RecordingDocumentTransactionListener()
    val backgroundableListener = RecordingDocumentTransactionListenerBackgroundable()
    project.get().messageBus.connect(testDisposable).subscribe(PsiDocumentTransactionListener.TOPIC, plainListener)
    project.get().messageBus.connect(testDisposable).subscribe(PsiDocumentTransactionListenerBackgroundable.TOPIC, backgroundableListener)

    invokeAndWaitIfNeeded {
      val file = file.get()
      val document = psiDocumentManager.getDocument(file)!!
      WriteCommandAction.runWriteCommandAction(project.get()) { document.insertString(0, "abc") }
      PlatformTestUtil.waitForAllDocumentsCommitted(10, TimeUnit.SECONDS)
    }

    assertTrue(plainListener.recorder.invoked.get() > 0)
    assertTrue(plainListener.recorder.onEdt.get() > 0)
    assertTrue(backgroundableListener.recorder.invoked.get() > 0)
    assertTrue(backgroundableListener.recorder.onEdt.get() < backgroundableListener.recorder.invoked.get())
  }


  class RecordingFileDocumentManagerListener : FileDocumentManagerListenerBackgroundable {
    val recorder = Recorder()
    override fun beforeDocumentSaving(document: Document) {
      recorder.recordUsage()
    }

    override fun afterDocumentSaved(document: Document) {
      recorder.recordUsage()
    }
  }

  fun testSaving(saveAction: (Document) -> Unit, disposable: Disposable) = timeoutRunBlocking {
    Registry.get("document.async.commit.with.coroutines").setValue(true, disposable)
    val file = file.get()
    val psiDocumentManager = PsiDocumentManager.getInstance(project.get())

    val document = runReadAction {
      psiDocumentManager.getDocument(file)!!
    }

    val plainListener = RecordingFileDocumentManagerListener()
    val backgroundableListener = RecordingFileDocumentManagerListener()
    application.messageBus.connect(disposable).subscribe(FileDocumentManagerListener.TOPIC, plainListener)
    application.messageBus.connect(disposable).subscribe(FileDocumentManagerListenerBackgroundable.TOPIC, backgroundableListener)

    withContext(Dispatchers.Default) {
      backgroundWriteAction {
        application.service<TransferredWriteActionService>().runOnEdtWithTransferredWriteActionAndWait {
          CommandProcessor.getInstance().executeCommand(project.get(), {
            document.insertString(0, " ")
          }, "test command", Any())
        }
      }
      saveAction(document)
    }

    assertTrue(plainListener.recorder.invoked.get() > 0)
    assertTrue(plainListener.recorder.onEdt.get() > 0)
    assertTrue(backgroundableListener.recorder.invoked.get() > 0)
    assertTrue(backgroundableListener.recorder.onEdt.get() == 0)
  }

  @TestFactory
  fun `saving runs FileDocumentManagerListener on background`(@TestDisposable testDisposable: Disposable): List<DynamicTest> = listOf(
    DynamicTest.dynamicTest("saveDocument") {
      testSaving({ FileDocumentManager.getInstance().saveDocument(it) }, testDisposable)
    },
    DynamicTest.dynamicTest("saveDocumentAsIs") {
      testSaving({ FileDocumentManager.getInstance().saveDocumentAsIs(it) }, testDisposable)
    },
    DynamicTest.dynamicTest("saveAllDocuments") {
      testSaving({ FileDocumentManager.getInstance().saveAllDocuments() }, testDisposable)
    },
  )
}