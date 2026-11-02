// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListenerBackgroundable
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.AnyPsiChangeListener
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.stubs.StubRecoveryExtraStep.DROP_PSI_CACHES
import com.intellij.psi.stubs.StubRecoveryExtraStep.REPARSE_FILE
import com.intellij.psi.stubs.StubRecoveryExtraStep.SAVE_DOCUMENT
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.unregisterService
import com.intellij.util.FileContentUtilCore
import com.intellij.util.concurrency.TransferredWriteActionService
import com.intellij.util.gist.GistManager
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IndexingDataKeys
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@TestApplication
class StubRecoveryServiceTest {
  companion object {
    private val project = projectFixture()
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val requestingProject = projectFixture()
  private val openProject = projectFixture(openAfterCreation = true)
  private val recoveryDirectory = tempPathFixture()

  @Test
  fun `merge pending extra steps for one file`(): Unit = recoveryTest {
    val currentProject = project.get()
    val psiChanges = trackPsiChanges()
    val file = LightVirtualFile("test.txt")
    val reparseEvents = trackReparseEvents(file)
    val savedDocuments = trackSavedDocuments()
    val document = readAction { requireNotNull(FileDocumentManager.getInstance().getDocument(file)) }
    replaceStubLoader {
      assertThat(psiChanges).isEmpty()
      assertThat(savedDocuments).isEmpty()
      assertThat(reparseEvents).isEmpty()
      rebuildStubTree(it)
    }
    val recovery = createRecoveryService(this)
    recovery.scheduleReindexing(currentProject, file)
    recovery.scheduleReindexing(currentProject, file, DROP_PSI_CACHES, SAVE_DOCUMENT)
    recovery.scheduleReindexing(currentProject, file, DROP_PSI_CACHES, REPARSE_FILE)
    recovery.scheduleReindexing(currentProject, file, SAVE_DOCUMENT, REPARSE_FILE)
    assertThat(file.rebuildRequested).isTrue()
    assertThat(psiChanges).isEmpty()

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(file.rebuildRequested).isTrue()
    assertThat(psiChanges).hasSize(2)
    assertThat(reparseEvents).hasSize(1)
    assertThat(savedDocuments).containsExactly(document)
  }

  @Test
  fun `repair the batch 100 ms after the last request`(): Unit = recoveryTest {
    val scheduler = repairScheduler
    val recovery = createRecoveryService(this)
    val file = LightVirtualFile("test.txt")
    val second = LightVirtualFile("second.txt")
    val reparseEvents = trackReparseEvents(file)
    val secondReparseEvents = trackReparseEvents(second)
    recovery.scheduleReindexing(null, file, REPARSE_FILE)
    assertThat(file.rebuildRequested).isTrue()

    scheduler.advanceTimeBy(99)
    scheduler.runCurrent()
    assertThat(reparseEvents).isEmpty()
    recovery.scheduleReindexing(null, file, REPARSE_FILE)
    recovery.scheduleReindexing(null, second, REPARSE_FILE)
    assertThat(second.rebuildRequested).isTrue()

    scheduler.advanceTimeBy(99)
    scheduler.runCurrent()
    assertThat(reparseEvents).isEmpty()
    assertThat(secondReparseEvents).isEmpty()

    scheduler.advanceTimeBy(1)
    scheduler.runCurrent()
    repeat(2) {
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(reparseEvents).hasSize(1)
    assertThat(secondReparseEvents).hasSize(1)
  }

  @Test
  fun `repair successive batches after requests stop`(): Unit = recoveryTest {
    val recovery = createRecoveryService(this)
    repeat(3) { batch ->
      val files = List(4) { LightVirtualFile("$batch-$it.txt") }
      val reparseEvents = files.map { trackReparseEvents(it) }
      for (file in files) {
        recovery.scheduleReindexing(null, file, REPARSE_FILE)
        assertThat(file.rebuildRequested).isTrue()
        repairScheduler.advanceTimeBy(25)
        assertThat(reparseEvents).allSatisfy { assertThat(it).isEmpty() }
      }
      repairScheduler.advanceTimeBy(75)
      repairScheduler.runCurrent()
      repeat(2) {
        withContext(Dispatchers.EDT) { }
        repairScheduler.runCurrent()
      }
      assertThat(reparseEvents).allSatisfy { assertThat(it).hasSize(1) }
    }
  }

  @ParameterizedTest
  @EnumSource(StubRecoveryExtraStep::class)
  fun `execute each extra step independently`(extraStep: StubRecoveryExtraStep): Unit = recoveryTest {
    val currentProject = project.get()
    val file = LightVirtualFile("test.txt")
    val document = readAction { requireNotNull(FileDocumentManager.getInstance().getDocument(file)) }
    val savedDocuments = trackSavedDocuments()
    val psiChanges = trackPsiChanges()
    val reparseEvents = trackReparseEvents(file)
    createRecoveryService(this).scheduleReindexing(currentProject, file, extraStep)

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(file.rebuildRequested).isTrue()
    val [expectedDocuments, expectedPsiChanges, expectedReparses] = when (extraStep) {
      DROP_PSI_CACHES -> Triple(emptyList(), 2, 0)
      SAVE_DOCUMENT -> Triple(listOf(document), 0, 0)
      REPARSE_FILE -> Triple(emptyList(), 0, 1)
    }
    assertThat(savedDocuments).containsExactlyElementsOf(expectedDocuments)
    assertThat(psiChanges).hasSize(expectedPsiChanges)
    assertThat(reparseEvents).hasSize(expectedReparses)
  }

  @Test
  fun `keep requests for different files separate`(): Unit = recoveryTest {
    val recovery = createRecoveryService(this)
    val first = LightVirtualFile("first.txt")
    val second = LightVirtualFile("second.txt")
    val document = readAction {
      requireNotNull(FileDocumentManager.getInstance().getDocument(first))
      requireNotNull(FileDocumentManager.getInstance().getDocument(second))
    }
    val savedDocuments = trackSavedDocuments()
    recovery.scheduleReindexing(null, first)
    recovery.scheduleReindexing(null, second, SAVE_DOCUMENT)
    assertThat(first.rebuildRequested).isTrue()
    assertThat(second.rebuildRequested).isTrue()
    assertThat(savedDocuments).isEmpty()

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(savedDocuments).containsExactly(document)
  }

  @Test
  fun `repair a batch without yielding the EDT`(): Unit = recoveryTest {
    val application = ApplicationManager.getApplication()
    val files = List(3) { LightVirtualFile("$it.txt") }
    val attempted = ArrayList<VirtualFile>()
    val observed = CompletableDeferred<List<VirtualFile>>()
    val manager = mock(FileDocumentManager::class.java)
    for (file in files) {
      val document = mock(Document::class.java)
      `when`(manager.getCachedDocument(file)).thenReturn(document)
      doAnswer {
        assertThat(application.isWriteIntentLockAcquired).isTrue()
        attempted.add(file)
        if (attempted.size == 1) application.invokeLater { observed.complete(attempted.toList()) }
        null
      }.`when`(manager).saveDocument(document)
    }
    application.replaceService(FileDocumentManager::class.java, manager, disposable)
    val recovery = createRecoveryService(this, StandardTestDispatcher(repairScheduler))
    for (file in files) recovery.scheduleReindexing(null, file, SAVE_DOCUMENT)
    assertThat(attempted).isEmpty()

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(observed.await()).containsExactlyElementsOf(files)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `schedule reindexing on the caller thread without cancellation`(onEdt: Boolean): Unit = recoveryTest {
    val file = LightVirtualFile("test.txt")
    val recovery = createRecoveryService(this)
    val dispatcher = if (onEdt) Dispatchers.EDT else Dispatchers.Default
    withContext(dispatcher) {
      readAction {
        val caller = Thread.currentThread()
        var requested = false
        replaceStubLoader {
          assertThat(Thread.currentThread()).isSameAs(caller)
          assertThat(Cancellation.currentJob()).isSameAs(NonCancellable)
          ProgressManager.checkCanceled()
          requested = true
        }
        recovery.scheduleReindexing(null, file)
        assertThat(requested).isTrue()
      }
    }
  }

  @Test
  fun `drop each project's caches once for a pending batch`(): Unit = recoveryTest {
    val firstProject = project.get()
    val secondProject = requestingProject.get()
    val firstChanges = trackPsiChanges(firstProject)
    val secondChanges = trackPsiChanges(secondProject)
    val first = LightVirtualFile("first.txt")
    val second = LightVirtualFile("second.txt")
    val third = LightVirtualFile("third.txt")
    val manager = FileDocumentManager.getInstance()
    val document = readAction {
      requireNotNull(manager.getDocument(first))
      requireNotNull(manager.getDocument(second))
      requireNotNull(manager.getDocument(third))
    }
    val savedDocuments = trackSavedDocuments()
    val reparseEvents = trackReparseEvents(second)
    val recovery = createRecoveryService(this)
    recovery.scheduleReindexing(firstProject, first, DROP_PSI_CACHES)
    recovery.scheduleReindexing(firstProject, second, DROP_PSI_CACHES, REPARSE_FILE)
    recovery.scheduleReindexing(secondProject, second, DROP_PSI_CACHES)
    recovery.scheduleReindexing(secondProject, third, SAVE_DOCUMENT)
    recovery.scheduleReindexing(secondProject, third, DROP_PSI_CACHES)
    assertThat(firstChanges).isEmpty()
    assertThat(secondChanges).isEmpty()

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(firstChanges).hasSize(2)
    assertThat(secondChanges).hasSize(2)
    assertThat(savedDocuments).containsExactly(document)
    assertThat(reparseEvents).hasSize(1)
    for (file in listOf(first, second, third)) {
      assertThat(file.rebuildRequested).isTrue()
    }

    recovery.scheduleReindexing(firstProject, first, DROP_PSI_CACHES)
    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(firstChanges).hasSize(4)
    assertThat(secondChanges).hasSize(2)
    assertThat(savedDocuments).containsExactly(document)
    assertThat(reparseEvents).hasSize(1)
  }

  @Test
  fun `repair files without a gist service`(): Unit = recoveryTest {
    val application = ApplicationManager.getApplication()
    val manager = mock(GistManager::class.java)
    val files = listOf(LightVirtualFile("first.txt"), LightVirtualFile("second.txt"))
    val reindexed = ArrayList<VirtualFile>()
    replaceStubLoader { reindexed.add(it) }
    application.replaceService(GistManager::class.java, manager, disposable)
    application.unregisterService(GistManager::class.java)
    try {
      val recovery = createRecoveryService(this)
      for (file in files) recovery.scheduleReindexing(null, file, SAVE_DOCUMENT)

      repeat(2) {
        repairScheduler.advanceUntilIdle()
        withContext(Dispatchers.EDT) { }
        repairScheduler.runCurrent()
      }

      assertThat(reindexed).containsExactlyInAnyOrderElementsOf(files)
    }
    finally {
      application.registerServiceInstance(GistManager::class.java, manager)
    }
  }

  @Test
  fun `save a modified document without write access`(): Unit = recoveryTest {
    val application = ApplicationManager.getApplication()
    val path = recoveryDirectory.get().resolve("test.txt")
    val file = withContext(Dispatchers.IO) {
      Files.writeString(path, "original")
      requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path))
    }
    val manager = FileDocumentManager.getInstance()
    val modifiedDocument = readAction { requireNotNull(manager.getDocument(file)) }
    edtWriteAction { modifiedDocument.setText("modified") }
    assertThat(manager.isDocumentUnsaved(modifiedDocument)).isTrue()
    var writeIntentDuringSave: Boolean? = null
    var writeAccessDuringSave: Boolean? = null
    val vetoer = object : FileDocumentSynchronizationVetoer() {
      override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean {
        if (document === modifiedDocument) {
          writeIntentDuringSave = application.isWriteIntentLockAcquired
          writeAccessDuringSave = application.isWriteAccessAllowed
        }
        return true
      }
    }
    FileDocumentSynchronizationVetoer.EP_NAME.point.registerExtension(vetoer, disposable)
    createRecoveryService(this).scheduleReindexing(null, file, SAVE_DOCUMENT)

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(writeIntentDuringSave).isTrue()
    assertThat(writeAccessDuringSave).isFalse()
    assertThat(manager.isDocumentUnsaved(modifiedDocument)).isFalse()
    val savedText = withContext(Dispatchers.IO) {
      ManagingFS.getInstance().flushPendingUpdates(file)
      Files.readString(path)
    }
    assertThat(savedText).isEqualTo("modified")
    assertThat(file.rebuildRequested).isTrue()
  }

  @Test
  fun `continue other repairs when one file fails`(): Unit = recoveryTest {
    val first = LightVirtualFile("first.txt")
    val second = LightVirtualFile("second.txt")
    val attempted = ArrayList<VirtualFile>()
    val diagnostic = IllegalStateException("The first repair failed")
    replaceStubLoader { file ->
      attempted.add(file)
      if (attempted.size == 1) throw diagnostic
      rebuildStubTree(file)
    }
    val errors = captureRepairErrors {
      val recovery = createRecoveryService(this)
      recovery.scheduleReindexing(null, first)
      recovery.scheduleReindexing(null, second)
    }

    assertThat(errors).containsExactly(diagnostic)
    assertThat(attempted).containsExactlyInAnyOrder(first, second)
    assertThat(attempted.last().rebuildRequested).isTrue()
  }

  @ParameterizedTest
  @EnumSource(StubRecoveryExtraStep::class)
  fun `retain reindexing after an extra step fails`(extraStep: StubRecoveryExtraStep): Unit = recoveryTest {
    val currentProject = project.get()
    val file = LightVirtualFile("test.txt")
    val diagnostic = IllegalStateException("The extra step failed")
    failExtraStep(extraStep, currentProject, file, diagnostic)
    val errors = captureRepairErrors {
      createRecoveryService(this).scheduleReindexing(currentProject, file, extraStep)
    }

    assertThat(file.rebuildRequested).isTrue()
    assertThat(errors).containsExactly(diagnostic)
  }

  @Test
  fun `retain reindexing when cache invalidation fails`(): Unit = recoveryTest {
    val currentProject = openProject.get()
    val file = LightVirtualFile("test.txt")
    val diagnostic = IllegalStateException("The cache drop failed")
    replaceStubLoader { it.rebuildRequested = true }
    val manager = mock(PsiManager::class.java)
    doAnswer {
      assertThat(file.rebuildRequested).isTrue()
      throw diagnostic
    }.`when`(manager).dropPsiCaches()
    currentProject.replaceService(PsiManager::class.java, manager, disposable)
    val errors = captureRepairErrors {
      createRecoveryService(this).scheduleReindexing(currentProject, file, DROP_PSI_CACHES)
    }

    assertThat(file.rebuildRequested).isTrue()
    assertThat(errors).containsExactly(diagnostic)
  }

  @Test
  fun `reparse and reindex after saving fails`(): Unit = recoveryTest {
    val file = LightVirtualFile("test.txt")
    val diagnostic = IllegalStateException("The save failed")
    val reparseEvents = trackReparseEvents(file)
    failExtraStep(SAVE_DOCUMENT, project.get(), file, diagnostic)
    val errors = captureRepairErrors {
      createRecoveryService(this).scheduleReindexing(null, file, SAVE_DOCUMENT, REPARSE_FILE)
    }

    assertThat(reparseEvents).hasSize(1)
    assertThat(file.rebuildRequested).isTrue()
    assertThat(errors).containsExactly(diagnostic)
  }

  @Test
  fun `preserve failures from both saving and reindexing`(): Unit = recoveryTest {
    val file = LightVirtualFile("test.txt")
    val saveFailure = IllegalStateException("The save failed")
    val reindexFailure = IllegalArgumentException("The reindex request failed")
    failExtraStep(SAVE_DOCUMENT, project.get(), file, saveFailure)
    replaceStubLoader { throw reindexFailure }
    val errors = captureRepairErrors {
      createRecoveryService(this).scheduleReindexing(null, file, SAVE_DOCUMENT)
    }

    assertThat(errors).containsExactly(reindexFailure, saveFailure)
  }

  @ParameterizedTest
  @EnumSource(StubRecoveryExtraStep::class)
  fun `cancel the batch when an extra step is canceled`(extraStep: StubRecoveryExtraStep): Unit = recoveryTest {
    val file = LightVirtualFile("test.txt")
    val manager = mock(GistManager::class.java)
    var cacheMergeFinished = false
    val token = AccessToken.create { cacheMergeFinished = true }
    `when`(manager.mergeDependentCacheInvalidations()).thenReturn(token)
    ApplicationManager.getApplication().replaceService(GistManager::class.java, manager, disposable)
    failExtraStep(extraStep, project.get(), file, ProcessCanceledException())
    val recovery = createRecoveryService(this)
    recovery.scheduleReindexing(project.get(), file, extraStep)
    val worker = recoveryWorker(recovery)

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(worker.isActive).isTrue()
    assertThat(file.rebuildRequested).isTrue()
    assertThat(cacheMergeFinished).isTrue()
  }

  @Test
  fun `discard the batch after saving is canceled`(): Unit = recoveryTest {
    val canceledFile = LightVirtualFile("canceled.txt")
    val otherFile = LightVirtualFile("other.txt")
    val canceledReparses = trackReparseEvents(canceledFile)
    val otherReparses = trackReparseEvents(otherFile)
    failExtraStep(SAVE_DOCUMENT, project.get(), canceledFile, ProcessCanceledException())
    val recovery = createRecoveryService(this)
    recovery.scheduleReindexing(null, canceledFile, SAVE_DOCUMENT, REPARSE_FILE)
    recovery.scheduleReindexing(null, otherFile, REPARSE_FILE)

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(canceledReparses).isEmpty()
    assertThat(canceledFile.rebuildRequested).isTrue()
    assertThat(otherReparses).isEmpty()
    assertThat(otherFile.rebuildRequested).isTrue()
    assertThat(recoveryWorker(recovery).isActive).isTrue()
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `propagate cancellation from synchronous reindexing`(cancelServiceScope: Boolean): Unit = recoveryTest {
    val first = LightVirtualFile("first.txt")
    val second = LightVirtualFile("second.txt")
    val attempted = ArrayList<VirtualFile>()
    val reparseEvents = trackReparseEvents(first)
    val recovery = createRecoveryService(this)
    val worker = recoveryWorker(recovery)
    replaceStubLoader { file ->
      attempted.add(file)
      if (file == first) {
        if (cancelServiceScope) cancelRecoveryService(recovery)
        throw ProcessCanceledException()
      }
      rebuildStubTree(file)
    }
    assertThrows<ProcessCanceledException> {
      recovery.scheduleReindexing(null, first, REPARSE_FILE)
    }
    recovery.scheduleReindexing(null, second)

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(worker.isCancelled).isEqualTo(cancelServiceScope)
    assertThat(coroutineContext.job.isActive).isTrue()
    assertThat(attempted).containsExactlyElementsOf(if (cancelServiceScope) listOf(first) else listOf(first, second))
    assertThat(first.rebuildRequested).isNull()
    assertThat(second.rebuildRequested == true).isEqualTo(!cancelServiceScope)
    assertThat(reparseEvents).isEmpty()
  }

  @Test
  fun `process new requests after an extra step is canceled`(): Unit = recoveryTest {
    val first = LightVirtualFile("first.txt")
    val second = LightVirtualFile("second.txt")
    failExtraStep(SAVE_DOCUMENT, project.get(), first, ProcessCanceledException())
    val recovery = createRecoveryService(this)
    recovery.scheduleReindexing(null, first, SAVE_DOCUMENT)
    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    recovery.scheduleReindexing(null, second)
    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(first.rebuildRequested).isTrue()
    assertThat(second.rebuildRequested).isTrue()
    assertThat(recoveryWorker(recovery).isActive).isTrue()
  }

  @Test
  @SystemProperty(propertyKey = "skip.index.repair", propertyValue = "true")
  @Suppress("SuspiciousPackagePrivateAccess")
  fun `skip automatic repairs when disabled`(): Unit = recoveryTest {
    val currentProject = project.get()
    val file = LightVirtualFile("test.txt")
    readAction { requireNotNull(FileDocumentManager.getInstance().getDocument(file)) }
    val psiChanges = trackPsiChanges()
    val savedDocuments = trackSavedDocuments()
    val reparseEvents = trackReparseEvents(file)
    val helper = StubProcessingHelper()
    helper.onInternalError(file)
    createRecoveryService(this).scheduleReindexing(currentProject, file, *StubRecoveryExtraStep.entries.toTypedArray())

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(file.rebuildRequested).isNull()
    assertThat(psiChanges).isEmpty()
    assertThat(savedDocuments).isEmpty()
    assertThat(reparseEvents).isEmpty()
    assertThat(helper.takeAccumulatedFilesWithIndexProblems()).isNull()
  }

  @Test
  fun `the stub loader requests reindexing before returning`() {
    val file = LightVirtualFile("test.txt")

    StubTreeLoader.getInstance().rebuildStubTree(file)

    assertThat(file.rebuildRequested).isTrue()
  }

  @Test
  fun `use the stub loader when direct reindexing is unsupported`(): Unit = recoveryTest {
    val file = LightVirtualFile("test.txt")
    val rebuilt = ArrayList<VirtualFile>()
    replaceStubLoader { rebuilt.add(it) }
    val index = mock(FileBasedIndex::class.java)
    doThrow(UnsupportedOperationException("Direct reindexing is not supported")).`when`(index).requestReindex(file)
    ApplicationManager.getApplication().replaceService(FileBasedIndex::class.java, index, disposable)

    createRecoveryService(this).scheduleReindexing(null, file)
    assertThat(rebuilt).containsExactly(file)
  }

  @Test
  fun `allow another repair after the pending request completes`(): Unit = recoveryTest {
    val recovery = createRecoveryService(this)
    val file = LightVirtualFile("test.txt")
    val document = readAction { requireNotNull(FileDocumentManager.getInstance().getDocument(file)) }
    val savedDocuments = trackSavedDocuments()
    val psiChanges = trackPsiChanges()
    val reparseEvents = trackReparseEvents(file)
    recovery.scheduleReindexing(project.get(), file)
    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }
    assertThat(file.rebuildRequested).isTrue()
    assertThat(savedDocuments).isEmpty()
    assertThat(psiChanges).isEmpty()
    assertThat(reparseEvents).isEmpty()
    file.rebuildRequested = null

    recovery.scheduleReindexing(project.get(), file, SAVE_DOCUMENT)
    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(file.rebuildRequested).isTrue()
    assertThat(savedDocuments).containsExactly(document)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `reindex a file when the requesting project is disposed`(disposeBeforeRequest: Boolean): Unit = recoveryTest {
    val currentProject = requestingProject.get()
    val recovery = createRecoveryService(this)
    val directory = recoveryDirectory.get()
    val file = withContext(Dispatchers.IO) {
      val path = Files.writeString(directory.resolve("test.txt"), "")
      requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path))
    }
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val projectManager = ProjectManagerEx.getInstanceEx()
        if (disposeBeforeRequest) projectManager.forceCloseProject(currentProject, save = false)
        recovery.scheduleReindexing(currentProject, file, DROP_PSI_CACHES)
        if (!disposeBeforeRequest) projectManager.forceCloseProject(currentProject, save = false)
      }
    }
    assertThat(currentProject.isDisposed).isTrue()

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(file.rebuildRequested).isTrue()
  }

  @Test
  fun `discard requests after the service scope stops`(): Unit = recoveryTest {
    val recovery = createRecoveryService(this)
    val file = LightVirtualFile("test.txt")
    val reparseEvents = trackReparseEvents(file)
    recovery.scheduleReindexing(null, file, REPARSE_FILE)
    assertThat(file.rebuildRequested).isTrue()
    cancelRecoveryService(recovery)
    file.rebuildRequested = null
    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }
    recovery.scheduleReindexing(null, file, *StubRecoveryExtraStep.entries.toTypedArray())
    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(file.rebuildRequested).isNull()
    assertThat(reparseEvents).isEmpty()
  }

  @Test
  fun `discard requests when the service stops before the worker starts`(): Unit = recoveryTest {
    val scope = childScope("StubRecoveryServiceTest", StandardTestDispatcher(repairScheduler))
    try {
      val recovery = StubRecoveryServiceImpl(scope)
      val file = LightVirtualFile("test.txt")
      val attempted = ArrayList<VirtualFile>()
      replaceStubLoader { attempted.add(it) }
      recovery.scheduleReindexing(null, file)
      scope.cancel()
      recovery.scheduleReindexing(null, file)

      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()

      assertThat(attempted).containsExactly(file)
      assertThat(scope.coroutineContext.job.isCompleted).isTrue()
    }
    finally {
      scope.cancel()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `stop repairs when saving cancels the service`(cancelOnLastSave: Boolean): Unit = recoveryTest {
    val files = listOf(LightVirtualFile("first.txt"), LightVirtualFile("second.txt"))
    val documents = files.associateWith { mock(Document::class.java) }
    val manager = mock(FileDocumentManager::class.java)
    val saved = ArrayList<VirtualFile>()
    val reparseEvents = files.associateWith { trackReparseEvents(it) }
    val recovery = createRecoveryService(this)
    for (file in files) {
      val document = documents.getValue(file)
      `when`(manager.getCachedDocument(file)).thenReturn(document)
      doAnswer {
        saved.add(file)
        if (!cancelOnLastSave || saved.size == files.size) cancelRecoveryService(recovery)
        null
      }.`when`(manager).saveDocument(document)
    }
    ApplicationManager.getApplication().replaceService(FileDocumentManager::class.java, manager, disposable)
    for (file in files) recovery.scheduleReindexing(null, file, SAVE_DOCUMENT, REPARSE_FILE)

    repeat(2) {
      repairScheduler.advanceUntilIdle()
      withContext(Dispatchers.EDT) { }
      repairScheduler.runCurrent()
    }

    assertThat(saved).containsExactlyElementsOf(if (cancelOnLastSave) files else files.take(1))
    assertThat(reparseEvents.getValue(files.first())).isEmpty()
    assertThat(files.first().rebuildRequested).isTrue()
    assertThat(reparseEvents.getValue(files.last())).isEmpty()
    assertThat(files.last().rebuildRequested).isTrue()
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `discard remaining repairs when the service stops`(cancelServiceScope: Boolean): Unit = recoveryTest {
    val repairScope = childScope("StubRecoveryServiceTest")
    val files = listOf(LightVirtualFile("first.txt"), LightVirtualFile("second.txt"))
    readAction {
      files.forEach { requireNotNull(FileDocumentManager.getInstance().getDocument(it)) }
    }
    val savedDocuments = trackSavedDocuments()
    val reparseEvents = files.associateWith { trackReparseEvents(it) }
    val attempted = ArrayList<VirtualFile>()
    val recovery = createRecoveryService(repairScope)
    replaceStubLoader { file ->
      attempted.add(file)
      rebuildStubTree(file)
      if (attempted.size == 1) {
        if (cancelServiceScope) cancelRecoveryService(recovery)
        else repairScope.cancel()
      }
    }
    try {
      for (file in files) {
        recovery.scheduleReindexing(null, file, SAVE_DOCUMENT, REPARSE_FILE)
      }
      repeat(2) {
        repairScheduler.advanceUntilIdle()
        withContext(Dispatchers.EDT) { }
        repairScheduler.runCurrent()
      }

      assertThat(attempted).hasSize(1)
      val repaired = files.single { it == attempted.single() }
      assertThat(savedDocuments).isEmpty()
      assertThat(reparseEvents.getValue(repaired)).isEmpty()
      assertThat(repaired.rebuildRequested).isTrue()
      val remaining = files.single { it != repaired }
      assertThat(reparseEvents.getValue(remaining)).isEmpty()
      assertThat(remaining.rebuildRequested).isNull()
    }
    finally {
      repairScope.cancel()
    }
  }

  @Test
  fun `register the recovery service`() {
    assertThat(StubRecoveryService.getInstance()).isInstanceOf(StubRecoveryServiceImpl::class.java)
  }

  @Test
  @RegistryKey(key = "daemon.suppress.stub.inconsistency.errors", value = "true")
  fun `log an inconsistency at debug level under wrapped highlighting progress`() {
    val logger = mock(Logger::class.java)
    val cause = IllegalStateException("test stub inconsistency")
    val indicator = object : EmptyProgressIndicator(), StubRecoveryService.HighlightingProgress {}
    ProgressManager.getInstance().runProcess(
      Runnable { StubRecoveryService.logStubInconsistency(project.get(), logger, "test inconsistency", cause) },
      SensitiveProgressWrapper(SensitiveProgressWrapper(indicator)),
    )
    verify(logger).debug("test inconsistency, isDumb= false, canRunSmart= true", cause)
    verifyNoMoreInteractions(logger)
    assertThat(indicator.isCanceled).isFalse()
  }

  @Test
  @RegistryKey(key = "daemon.suppress.stub.inconsistency.errors", value = "false")
  fun `report an inconsistency when highlighting suppression is disabled`() {
    val logger = mock(Logger::class.java)
    val cause = IllegalStateException("test stub inconsistency")
    val indicator = object : EmptyProgressIndicator(), StubRecoveryService.HighlightingProgress {}
    ProgressManager.getInstance().runProcess(
      Runnable { StubRecoveryService.logStubInconsistency(project.get(), logger, "test inconsistency", cause) }, indicator,
    )
    verify(logger).error("test inconsistency, isDumb= false, canRunSmart= true", cause)
    verifyNoMoreInteractions(logger)
  }

  @Test
  @RegistryKey(key = "daemon.suppress.stub.inconsistency.errors", value = "true")
  fun `report an inconsistency outside highlighting`() {
    val logger = mock(Logger::class.java)
    val cause = IllegalStateException("test stub inconsistency")
    ProgressManager.getInstance().runProcess(
      Runnable { StubRecoveryService.logStubInconsistency(project.get(), logger, "test inconsistency", cause) }, EmptyProgressIndicator(),
    )
    verify(logger).error("test inconsistency, isDumb= false, canRunSmart= true", cause)
    verifyNoMoreInteractions(logger)
  }

  @Test
  @RegistryKey(key = "daemon.suppress.stub.inconsistency.errors", value = "true")
  fun `report an inconsistency when the project cannot run smart`() {
    val testProject = project.get()
    val dumbTask = DumbModeTestUtils.startEternalDumbModeTask(testProject)
    try {
      assertThat(DumbService.getInstance(testProject).canRunSmart()).isFalse
      val logger = mock(Logger::class.java)
      val cause = IllegalStateException("test stub inconsistency")
      val indicator = object : EmptyProgressIndicator(), StubRecoveryService.HighlightingProgress {}
      ProgressManager.getInstance().runProcess(
        Runnable { StubRecoveryService.logStubInconsistency(testProject, logger, "test inconsistency", cause) },
        indicator,
      )
      verify(logger).error("test inconsistency, isDumb= true, canRunSmart= false", cause)
      verifyNoMoreInteractions(logger)
    }
    finally {
      DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(testProject, dumbTask)
    }
  }

  private var VirtualFile.rebuildRequested: Boolean?
    get() = getUserData(IndexingDataKeys.REBUILD_REQUESTED)
    set(value) = putUserData(IndexingDataKeys.REBUILD_REQUESTED, value)

  private fun replaceStubLoader(rebuild: StubTreeLoader.(VirtualFile) -> Unit) {
    val delegate = StubTreeLoader.getInstance()
    ApplicationManager.getApplication().replaceService(StubTreeLoader::class.java, object : CoreStubTreeLoader() {
      override fun rebuildStubTree(virtualFile: VirtualFile) = delegate.rebuild(virtualFile)
    }, disposable)
  }

  private fun failExtraStep(extraStep: StubRecoveryExtraStep, currentProject: Project, file: VirtualFile, failure: Throwable) {
    val application = ApplicationManager.getApplication()
    when (extraStep) {
      DROP_PSI_CACHES -> {
        val manager = mock(PsiManager::class.java)
        doThrow(failure).`when`(manager).dropPsiCaches()
        currentProject.replaceService(PsiManager::class.java, manager, disposable)
      }
      SAVE_DOCUMENT -> {
        val manager = mock(FileDocumentManager::class.java)
        val document = mock(Document::class.java)
        `when`(manager.getCachedDocument(file)).thenReturn(document)
        doThrow(failure).`when`(manager).saveDocument(document)
        application.replaceService(FileDocumentManager::class.java, manager, disposable)
      }
      REPARSE_FILE -> {
        val service = object : TransferredWriteActionService {
          override fun runOnEdtWithTransferredWriteActionAndWait(action: Runnable) = action.run()

          override fun runOnBackgroundThreadWithTransferredWriteActionAndWait(action: Runnable) = throw failure
        }
        application.replaceService(TransferredWriteActionService::class.java, service, disposable)
      }
    }
  }

  private fun trackSavedDocuments(): List<Document> {
    val documents = ArrayList<Document>()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(
      FileDocumentManagerListenerBackgroundable.TOPIC,
      object : FileDocumentManagerListenerBackgroundable {
        override fun beforeAnyDocumentSaving(document: Document, explicit: Boolean) {
          documents.add(document)
        }
      },
    )
    return documents
  }

  private fun trackReparseEvents(file: LightVirtualFile): List<VFileEvent> {
    val reparseEvents = ArrayList<VFileEvent>()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        reparseEvents.addAll(events.filter { it.file == file && it.requestor == FileContentUtilCore.FORCE_RELOAD_REQUESTOR })
      }
    })
    return reparseEvents
  }

  private fun trackPsiChanges(currentProject: Project = project.get()): List<Boolean> {
    PsiManager.getInstance(currentProject)
    val changes = ArrayList<Boolean>()
    currentProject.messageBus.connect(disposable).subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, object : AnyPsiChangeListener {
      override fun beforePsiChanged(isPhysical: Boolean) {
        changes.add(isPhysical)
      }
    })
    return changes
  }

  private suspend fun captureRepairErrors(action: CoroutineScope.() -> Unit): List<Throwable> {
    return coroutineScope {
      val errors = ArrayList<Throwable>()
      val errorProcessor = object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
          errors.add(requireNotNull(t))
          return Action.NONE
        }
      }
      val firstService = recoveryServices.size
      try {
        LoggedErrorProcessor.executeWith(errorProcessor).use {
          action()
          repeat(2) {
            repairScheduler.advanceUntilIdle()
            withContext(Dispatchers.EDT) { }
            repairScheduler.runCurrent()
          }
        }
      }
      finally {
        for (service in recoveryServices.drop(firstService)) cancelRecoveryService(service)
      }
      errors
    }
  }

  private val recoveryServices = ArrayList<StubRecoveryServiceImpl>()
  private val recoveryServiceScopes = mutableMapOf<StubRecoveryServiceImpl, CoroutineScope>()
  private val repairScheduler = TestCoroutineScheduler()

  private fun createRecoveryService(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(repairScheduler),
  ): StubRecoveryServiceImpl {
    val serviceScope = scope.childScope("StubRecoveryServiceTest", dispatcher)
    return StubRecoveryServiceImpl(serviceScope).also {
      recoveryServices.add(it)
      recoveryServiceScopes[it] = serviceScope
    }
  }

  private fun cancelRecoveryService(service: StubRecoveryServiceImpl) {
    recoveryServiceScopes.getValue(service).cancel()
  }

  private fun recoveryWorker(service: StubRecoveryServiceImpl) =
    recoveryServiceScopes.getValue(service).coroutineContext.job.children.single()

  private fun recoveryTest(action: suspend CoroutineScope.() -> Unit): Unit = timeoutRunBlocking {
    try {
      IndexingTestUtil.suspendUntilIndexesAreReady(openProject.get())
      action()
    }
    finally {
      for (service in recoveryServices) cancelRecoveryService(service)
      repairScheduler.runCurrent()
    }
  }

}
