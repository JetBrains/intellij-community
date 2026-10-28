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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitForAllDocumentsCommitted
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.concurrent.CopyOnWriteArrayList
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
      waitForAllDocumentsCommitted(10, TimeUnit.SECONDS)
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

  /** Counts the physical changes, and the ones on the EDT. It fits both any-PSI-change topics. */
  class RecordingAnyPsiChangeListener : AnyPsiChangeListenerBackgroundable {
    val recorder = Recorder()

    override fun beforePsiChanged(isPhysical: Boolean) {
      if (isPhysical) recorder.recordUsage()
    }

    override fun afterPsiChanged(isPhysical: Boolean) {
      if (isPhysical) recorder.recordUsage()
    }
  }

  @Test
  fun `dropping the resolve caches in a background write action notifies a legacy subscriber on EDT`(
    @TestDisposable testDisposable: Disposable,
  ) {
    val legacyListener = RecordingAnyPsiChangeListener()
    val backgroundableListener = RecordingAnyPsiChangeListener()
    val connection = project.get().messageBus.connect(testDisposable)
    connection.subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, legacyListener)
    connection.subscribe(AnyPsiChangeListenerBackgroundable.TOPIC, backgroundableListener)
    val psiManager = PsiManagerEx.getInstanceEx(project.get())

    timeoutRunBlocking {
      withContext(Dispatchers.Default) {
        backgroundWriteAction { psiManager.dropResolveCaches() }
      }
    }

    assertTrue(legacyListener.recorder.invoked.get() > 0, "The legacy subscriber got no physical PSI change")
    assertEquals(legacyListener.recorder.invoked.get(), legacyListener.recorder.onEdt.get(),
                 "The legacy subscriber must always run on EDT")
    assertTrue(backgroundableListener.recorder.invoked.get() > 0, "The backgroundable subscriber got no physical PSI change")
    assertTrue(backgroundableListener.recorder.onEdt.get() < backgroundableListener.recorder.invoked.get(),
               "The backgroundable subscriber must run in place, off EDT")
  }

  /** Logs each notification of both any-PSI-change topics, in the order of arrival. */
  class AnyPsiChangeLog {
    data class Entry(val isLegacy: Boolean, val isBefore: Boolean, val isPhysical: Boolean, val thread: Thread, val onEdt: Boolean)

    val entries: MutableList<Entry> = CopyOnWriteArrayList()

    fun subscribe(project: Project, disposable: Disposable) {
      val connection = project.messageBus.connect(disposable)
      connection.subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, listener(isLegacy = true))
      connection.subscribe(AnyPsiChangeListenerBackgroundable.TOPIC, listener(isLegacy = false))
    }

    private fun listener(isLegacy: Boolean) = object : AnyPsiChangeListenerBackgroundable {
      override fun beforePsiChanged(isPhysical: Boolean) = record(isBefore = true, isPhysical)

      override fun afterPsiChanged(isPhysical: Boolean) = record(isBefore = false, isPhysical)

      private fun record(isBefore: Boolean, isPhysical: Boolean) {
        entries.add(Entry(isLegacy, isBefore, isPhysical, Thread.currentThread(), EDT.isCurrentThreadEdt()))
      }
    }

    fun physical(): List<Entry> = entries.filter { it.isPhysical }

    fun on(thread: Thread): List<Entry> = entries.filter { it.thread === thread }
  }

  @Test
  fun `a public physical change in a background write action notifies the legacy subscriber on EDT, between the backgroundable ones`(
    @TestDisposable testDisposable: Disposable,
  ) {
    val log = AnyPsiChangeLog()
    log.subscribe(project.get(), testDisposable)
    val psiManager = PsiManagerEx.getInstanceEx(project.get())

    val caller = timeoutRunBlocking {
      withContext(Dispatchers.Default) {
        backgroundWriteAction {
          psiManager.beforeChange(true)
          psiManager.afterChange(true)
          Thread.currentThread()
        }
      }
    }

    val actual = log.physical().map { listOf(it.isLegacy, it.isBefore, it.onEdt, it.thread === caller) }
    val expected = listOf(
      listOf(false, true, false, true),
      listOf(true, true, true, false),
      listOf(true, false, true, false),
      listOf(false, false, false, true),
    )
    assertEquals(expected, actual, "Each entry is [isLegacy, isBefore, onEdt, on the caller thread]")
  }

  @Test
  fun `a background document commit notifies the legacy subscriber on EDT, between the backgroundable ones`(
    @TestDisposable testDisposable: Disposable,
  ) {
    Registry.get("document.async.commit.with.coroutines").setValue(true, testDisposable)
    val psiDocumentManager = PsiDocumentManager.getInstance(project.get())
    val log = AnyPsiChangeLog()
    log.subscribe(project.get(), testDisposable)

    invokeAndWaitIfNeeded {
      val document = psiDocumentManager.getDocument(file.get())!!
      WriteCommandAction.runWriteCommandAction(project.get()) { document.insertString(0, "ghi") }
      waitForAllDocumentsCommitted(10, TimeUnit.SECONDS)
    }

    val physical = log.physical()
    assertTrue(physical.any { !it.isLegacy && it.isBefore && !it.onEdt }, "The commit sent no before-event off EDT")
    assertTrue(physical.any { !it.isLegacy && !it.isBefore && !it.onEdt }, "The commit sent no after-event off EDT")
    assertTrue(physical.filter { it.isLegacy }.all { it.onEdt }, "The legacy subscriber must always run on EDT")
    // A before-event notifies the backgroundable topic first. An after-event notifies the legacy topic first.
    assertEquals(0, physical.size % 2, "Each change must notify both topics: $physical")
    for ((first, second) in physical.chunked(2)) {
      assertEquals(first.isBefore, second.isBefore, "Both topics must get the same event: $physical")
      assertEquals(!first.isBefore, first.isLegacy, "The topics are in the wrong order: $physical")
      assertEquals(first.isBefore, second.isLegacy, "The topics are in the wrong order: $physical")
    }
  }

  @Test
  fun `a physical change without the write lock notifies the legacy subscriber in place`(@TestDisposable testDisposable: Disposable) {
    val log = AnyPsiChangeLog()
    log.subscribe(project.get(), testDisposable)
    val psiManager = PsiManagerEx.getInstanceEx(project.get())

    val caller = timeoutRunBlocking {
      withContext(Dispatchers.Default) {
        psiManager.beforeChange(true)
        psiManager.afterChange(true)
        Thread.currentThread()
      }
    }

    val actual = log.on(caller).map { listOf(it.isLegacy, it.isBefore, it.isPhysical) }
    val expected = listOf(
      listOf(false, true, true),
      listOf(true, true, true),
      listOf(true, false, true),
      listOf(false, false, true),
    )
    assertEquals(expected, actual, "Each entry is [isLegacy, isBefore, isPhysical]")
  }

  @Test
  fun `a non-physical change in a background write action notifies the legacy subscriber in place`(
    @TestDisposable testDisposable: Disposable,
  ) {
    val log = AnyPsiChangeLog()
    log.subscribe(project.get(), testDisposable)
    val psiManager = PsiManagerEx.getInstanceEx(project.get())

    val caller = timeoutRunBlocking {
      withContext(Dispatchers.Default) {
        backgroundWriteAction {
          psiManager.beforeChange(false)
          psiManager.afterChange(false)
          Thread.currentThread()
        }
      }
    }

    val actual = log.on(caller).map { listOf(it.isLegacy, it.isBefore, it.isPhysical) }
    val expected = listOf(
      listOf(false, true, false),
      listOf(true, true, false),
      listOf(true, false, false),
      listOf(false, false, false),
    )
    assertEquals(expected, actual, "Each entry is [isLegacy, isBefore, isPhysical]")
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
      waitForAllDocumentsCommitted(10, TimeUnit.SECONDS)
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