// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.mock.Mock
import com.intellij.openapi.fileEditor.CreatedFileEditorSink
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupEditorPreparationTest {
  @Test
  fun preparationStartsBeforePublication() = runTest {
    val owner = backgroundScope.childScope("startup preparation")
    val windowAdded = CompletableDeferred<Unit>()
    val finishHandling = CompletableDeferred<Unit>()
    var creations = 0
    var sourceFinished = false
    val expected = EditorCompositeModel(emptyList(), CreatedFileEditorSink())
    val model = prepareStartupEditorModel(flow {
      creations++
      emit(expected)
      sourceFinished = true
    }, owner, windowAdded)

    runCurrent()
    assertEquals(1, creations)
    assertFalse(sourceFinished)

    val received = CompletableDeferred<EditorCompositeModel>()
    val consumer = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
      model.collect {
        it.createdEditors!!.claim()
        received.complete(it)
        finishHandling.await()
      }
    }
    assertFalse(received.isCompleted)
    windowAdded.complete(Unit)
    runCurrent()
    assertSame(expected, received.await())
    assertEquals(1, creations)
    assertFalse(sourceFinished)

    finishHandling.complete(Unit)
    runCurrent()
    consumer.join()
    assertTrue(sourceFinished)
    owner.cancel()
  }

  @Test
  fun closingBeforeCollectionReleasesPreparedModel() = runTest {
    TrackedEditorModel().use { prepared ->
      val owner = backgroundScope.childScope("startup preparation")
      val model = prepareStartupEditorModel(prepared.source, owner, CompletableDeferred(Unit))

      runCurrent()
      assertEquals(listOf(prepared.editor), prepared.sink.toList())
      assertEquals(0, prepared.disposals)
      owner.cancel()
      runCurrent()
      assertTrue(prepared.cleanupStarted)
      assertEquals(1, prepared.disposals)

      val consumer = backgroundScope.async { model.collect() }
      runCurrent()
      assertTrue(consumer.isCancelled)
      assertEquals(1, prepared.disposals)
    }
  }

  @Test
  fun failureBeforeCollectionReachesConsumer() = runTest {
    val owner = backgroundScope.childScope("startup preparation")
    val failure = IllegalStateException("provider failed")
    val model = prepareStartupEditorModel(flow { throw failure }, owner, CompletableDeferred(Unit))

    runCurrent()
    val consumer = owner.async { model.collect() }
    runCurrent()

    assertTrue(consumer.isCompleted)
    val actualFailure = assertInstanceOf(IllegalStateException::class.java, consumer.getCompletionExceptionOrNull())
    assertEquals(failure.message, actualFailure.message)
    owner.cancel()
  }

  @Test
  fun failureAfterDeliveryReachesConsumer() = runTest {
    TrackedEditorModel().use { prepared ->
      val owner = backgroundScope.childScope("startup preparation")
      val failure = IllegalStateException("source failed")
      val model = prepareStartupEditorModel(flow {
        prepared.source.collect { emit(it) }
        throw failure
      }, owner, CompletableDeferred(Unit))
      val consumer = owner.async {
        model.collect { it.createdEditors!!.claim() }
      }
      try {
        runCurrent()

        assertTrue(consumer.isCompleted)
        val actualFailure = assertInstanceOf(IllegalStateException::class.java, consumer.getCompletionExceptionOrNull())
        assertEquals(failure.message, actualFailure.message)
        assertTrue(prepared.cleanupStarted)
        assertEquals(0, prepared.disposals)
      }
      finally {
        owner.cancel()
      }
    }
  }

  @Test
  fun sourceCancellationClosesDeliveryBeforePreparationCompletes() = runTest {
    TrackedEditorModel().use { prepared ->
      val owner = backgroundScope.childScope("startup preparation")
      val sourceJob = CompletableDeferred<Job>()
      val finishCleanup = CompletableDeferred<Unit>()
      val windowAdded = CompletableDeferred<Unit>()
      val model = prepareStartupEditorModel(flow {
        try {
          coroutineScope {
            sourceJob.complete(coroutineContext.job)
            prepared.source.collect { emit(it) }
          }
        }
        finally {
          withContext(NonCancellable) {
            finishCleanup.await()
          }
        }
      }, owner, windowAdded)
      try {
        runCurrent()
        assertEquals(listOf(prepared.editor), prepared.sink.toList())

        sourceJob.await().cancel()
        runCurrent()
        assertTrue(prepared.cleanupStarted)
        assertEquals(1, prepared.disposals)

        var delivered = false
        val consumer = owner.async {
          model.collect {
            delivered = true
            it.createdEditors!!.claim()
          }
        }
        windowAdded.complete(Unit)
        runCurrent()

        assertTrue(consumer.isCompleted)
        val failure = assertInstanceOf(IllegalStateException::class.java, consumer.getCompletionExceptionOrNull())
        assertEquals("Startup editor delivery is closed", failure.message)
        assertFalse(delivered)
      }
      finally {
        finishCleanup.complete(Unit)
        owner.cancel()
        runCurrent()
      }
    }
  }

  @Test
  fun cancellationBeforeProducerStartsReachesConsumer() = runTest {
    val owner = backgroundScope.childScope("startup preparation")
    owner.cancel()
    var started = false
    val model = prepareStartupEditorModel(flow {
      started = true
      emit(EditorCompositeModel(emptyList(), CreatedFileEditorSink()))
    }, owner, CompletableDeferred(Unit))

    val consumer = backgroundScope.async { model.collect() }
    runCurrent()

    assertFalse(started)
    assertTrue(consumer.isCancelled)
  }

  @Test
  fun cancellingConsumerBeforeWindowMountReleasesModel() = runTest {
    TrackedEditorModel().use { prepared ->
      val owner = backgroundScope.childScope("startup preparation")
      val model = prepareStartupEditorModel(prepared.source, owner, CompletableDeferred())
      try {
        runCurrent()
        assertEquals(listOf(prepared.editor), prepared.sink.toList())
        assertEquals(0, prepared.disposals)
        val consumer = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { model.collect() }
        consumer.cancelAndJoin()
        runCurrent()

        assertTrue(prepared.cleanupStarted)
        assertEquals(1, prepared.disposals)
      }
      finally {
        owner.cancel()
      }
    }
  }

  @Test
  fun cleanupWaitsForConsumerToLeaveDelivery() = runTest {
    TrackedEditorModel().use { prepared ->
      val owner = backgroundScope.childScope("startup preparation")
      val finishAdoption = CompletableDeferred<Unit>()
      val deliveryStarted = CompletableDeferred<Unit>()
      val model = prepareStartupEditorModel(prepared.source, owner, CompletableDeferred(Unit))
      val consumer = backgroundScope.launch {
        model.collect {
          assertSame(prepared.editor, it.fileEditorAndProviderList.single().fileEditor)
          deliveryStarted.complete(Unit)
          finishAdoption.await()
        }
      }
      try {
        runCurrent()
        assertTrue(deliveryStarted.isCompleted)

        owner.cancel()
        runCurrent()
        assertFalse(prepared.cleanupStarted)
        assertEquals(0, prepared.disposals)

        finishAdoption.complete(Unit)
        runCurrent()
        consumer.join()
        assertTrue(prepared.cleanupStarted)
        assertEquals(1, prepared.disposals)
      }
      finally {
        finishAdoption.complete(Unit)
        consumer.cancel()
        owner.cancel()
      }
    }
  }

  @Test
  fun cancellingConsumerBeforeClaimReleasesEditor() = runTest {
    TrackedEditorModel().use { prepared ->
      val owner = backgroundScope.childScope("startup preparation")
      val deliveryStarted = CompletableDeferred<Unit>()
      val model = prepareStartupEditorModel(prepared.source, owner, CompletableDeferred(Unit))
      val consumer = owner.launch {
        model.collect {
          deliveryStarted.complete(Unit)
          awaitCancellation()
        }
      }
      try {
        runCurrent()
        assertTrue(deliveryStarted.isCompleted)
        assertEquals(0, prepared.disposals)

        consumer.cancelAndJoin()
        runCurrent()
        assertTrue(prepared.cleanupStarted)
        assertEquals(1, prepared.disposals)
      }
      finally {
        owner.cancel()
      }
    }
  }

  @Test
  fun cancellingConsumerAfterClaimPreservesEditor() = runTest {
    TrackedEditorModel().use { prepared ->
      val owner = backgroundScope.childScope("startup preparation")
      val claimed = CompletableDeferred<Unit>()
      val model = prepareStartupEditorModel(prepared.source, owner, CompletableDeferred(Unit))
      val consumer = owner.launch {
        model.collect {
          it.createdEditors!!.claim()
          claimed.complete(Unit)
          awaitCancellation()
        }
      }
      try {
        runCurrent()
        assertTrue(claimed.isCompleted)
        assertTrue(prepared.sink.toList().isEmpty())
        assertFalse(prepared.cleanupStarted)

        consumer.cancelAndJoin()
        runCurrent()
        assertTrue(prepared.cleanupStarted)
        assertEquals(0, prepared.disposals)

        Disposer.dispose(prepared.editor)
        assertEquals(1, prepared.disposals)
      }
      finally {
        owner.cancel()
      }
    }
  }

  @Test
  fun consumerFailureBeforeClaimReleasesEditor() = runTest {
    TrackedEditorModel().use { prepared ->
      val owner = backgroundScope.childScope("startup preparation")
      val model = prepareStartupEditorModel(prepared.source, owner, CompletableDeferred(Unit))
      val failure = IllegalStateException("adoption failed")
      val consumer = owner.async {
        model.collect { throw failure }
      }
      try {
        runCurrent()

        val actualFailure = assertInstanceOf(IllegalStateException::class.java, consumer.getCompletionExceptionOrNull())
        assertEquals(failure.message, actualFailure.message)
        assertTrue(prepared.cleanupStarted)
        assertEquals(1, prepared.disposals)
      }
      finally {
        owner.cancel()
      }
    }
  }

  @Test
  fun secondConsumerCannotStealModel() = runTest {
    val owner = backgroundScope.childScope("startup preparation")
    val windowAdded = CompletableDeferred<Unit>()
    var deliveries = 0
    val model = prepareStartupEditorModel(flow {
      emit(EditorCompositeModel(emptyList(), CreatedFileEditorSink()))
    }, owner, windowAdded)
    val first = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
      model.collect {
        it.createdEditors!!.claim()
        deliveries++
      }
    }
    val second = owner.async { model.collect() }

    runCurrent()
    assertInstanceOf(IllegalStateException::class.java, second.getCompletionExceptionOrNull())
    assertTrue(first.isActive)

    windowAdded.complete(Unit)
    runCurrent()
    first.join()
    assertEquals(1, deliveries)
    owner.cancel()
  }

  @Test
  fun progressReportedBeforeCollectionReachesConsumer() = runTest {
    val owner = backgroundScope.childScope("startup preparation")
    val finishAdoption = CompletableDeferred<Unit>()
    val model = prepareStartupEditorModel(flow {
      reportRawProgress { reporter ->
        reporter.text("preparing")
        emit(EditorCompositeModel(emptyList(), CreatedFileEditorSink()))
      }
    }, owner, CompletableDeferred(Unit))
    runCurrent()

    val progress = backgroundScope.createProgressPipe()
    val reported = backgroundScope.async {
      progress.progressUpdates().first { it.text == "preparing" }
    }
    val consumer = backgroundScope.launch {
      progress.collectProgressUpdates {
        model.collect {
          finishAdoption.await()
          it.createdEditors!!.claim()
        }
      }
    }
    runCurrent()
    assertEquals("preparing", reported.getCompleted().text)

    finishAdoption.complete(Unit)
    runCurrent()
    consumer.join()
    owner.cancel()
  }

  private class TrackedEditorModel : AutoCloseable {
    var disposals: Int = 0
      private set
    var cleanupStarted: Boolean = false
      private set

    val editor: FileEditor = object : Mock.MyFileEditor() {
      override fun dispose() {
        disposals++
      }
    }
    val sink = CreatedFileEditorSink()
    private val provider = object : FileEditorProvider {
      override fun accept(project: Project, file: VirtualFile): Boolean = true
      override fun createEditor(project: Project, file: VirtualFile): FileEditor = editor
      override fun getEditorTypeId(): String = "startup-test"
      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }
    val source = flow {
      sink.register(editor)
      try {
        emit(EditorCompositeModel(listOf(FileEditorWithProvider(editor, provider)), sink))
        sink.awaitClaimed()
      }
      finally {
        cleanupStarted = true
        for (abandoned in sink.toList()) {
          Disposer.dispose(abandoned)
        }
      }
    }

    override fun close() {
      Disposer.dispose(editor)
    }
  }
}
