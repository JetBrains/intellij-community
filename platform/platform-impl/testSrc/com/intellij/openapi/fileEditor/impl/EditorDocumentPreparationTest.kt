// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

@TestApplication
internal class EditorDocumentPreparationTest {
  @Test
  fun loadsOnceAfterExplicitStart(): Unit = timeoutRunBlocking {
    val owner = childScope("Document preparation test")
    val calls = AtomicInteger()
    val expected = DocumentImpl("text")
    val preparation = EditorDocumentPreparation(owner, LightVirtualFile("early.txt")) {
      calls.incrementAndGet()
      expected
    }
    try {
      assertThat(calls.get()).isZero()
      preparation.start()
      preparation.start()
      assertThat(preparation.await()).isSameAs(expected)
      assertThat(preparation.await()).isSameAs(expected)
      assertThat(calls.get()).isEqualTo(1)
    }
    finally {
      owner.cancel()
    }
  }

  @Test
  fun runsInBackgroundWhileEdtMountsTab(): Unit = timeoutRunBlocking {
    val owner = childScope("Document preparation dispatcher")
    val started = CompletableFuture<Boolean>()
    val finish = CompletableDeferred<Unit>()
    val preparation = EditorDocumentPreparation(owner, LightVirtualFile("background.txt")) {
      started.complete(SwingUtilities.isEventDispatchThread())
      finish.await()
      null
    }
    try {
      withContext(Dispatchers.EDT) {
        preparation.start()
        assertThat(started.get(5, TimeUnit.SECONDS)).isFalse()
      }
      finish.complete(Unit)
      assertThat(preparation.await()).isNull()
    }
    finally {
      owner.cancel()
    }
  }

  @Test
  fun ownerCancellationBeforeStartSkipsLoading(): Unit = timeoutRunBlocking {
    val owner = childScope("Cancelled document preparation")
    val calls = AtomicInteger()
    val preparation = EditorDocumentPreparation(owner, LightVirtualFile("cancelled.txt")) {
      calls.incrementAndGet()
      null
    }
    owner.cancel()
    preparation.start()
    assertThat(runCatching { preparation.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
    assertThat(calls.get()).isZero()
  }

  @Test
  fun ownerCancellationStopsRunningLoad(): Unit = timeoutRunBlocking {
    val owner = childScope("Running document preparation")
    val started = CompletableDeferred<Unit>()
    val stopped = CompletableDeferred<Unit>()
    val preparation = EditorDocumentPreparation(owner, LightVirtualFile("running.txt")) {
      try {
        started.complete(Unit)
        awaitCancellation()
      }
      finally {
        stopped.complete(Unit)
      }
    }
    preparation.start()
    started.await()
    owner.cancel()
    stopped.await()
    assertThat(runCatching { preparation.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
  }

  @Test
  fun consumerObservesFailureFromSupervisedTask(): Unit = timeoutRunBlocking {
    val owner = childScope("Failed document preparation")
    val failure = IOException("document load failed")
    val preparation = EditorDocumentPreparation(owner, LightVirtualFile("failed.txt")) { throw failure }
    try {
      preparation.start()
      assertThat(runCatching { preparation.await() }.exceptionOrNull())
        .isInstanceOf(IOException::class.java)
        .hasMessage(failure.message)
      assertThat(owner.isActive).isTrue()
    }
    finally {
      owner.cancel()
    }
  }

  @Test
  fun lateObserverSeesCurrentAndLaterProgress(): Unit = timeoutRunBlocking {
    val owner = childScope("Document preparation progress")
    val continueLoading = CompletableDeferred<Unit>()
    val finish = CompletableDeferred<Unit>()
    val preparation = EditorDocumentPreparation(owner, LightVirtualFile("progress.txt")) {
      reportRawProgress { reporter ->
        reporter.text("reading")
        continueLoading.await()
        reporter.text("decoding")
        finish.await()
        null
      }
    }
    try {
      preparation.start()
      assertThat(preparation.progress.progressUpdates().first { it.text == "reading" }.text).isEqualTo("reading")
      continueLoading.complete(Unit)
      assertThat(preparation.progress.progressUpdates().first { it.text == "decoding" }.text).isEqualTo("decoding")
      finish.complete(Unit)
      preparation.await()
      assertThat(preparation.progress.progressUpdates().first { it.fraction == 1.0 }.fraction).isEqualTo(1.0)
    }
    finally {
      owner.cancel()
    }
  }

  @Test
  fun loadsDocumentThroughDocumentManager(): Unit = timeoutRunBlocking {
    val owner = childScope("Document manager preparation")
    val file = LightVirtualFile("document.txt", "content")
    val preparation = EditorDocumentPreparation(owner, file)
    try {
      preparation.start()
      val document = preparation.await()
      assertThat(document?.text).isEqualTo("content")
      assertThat(service<FileDocumentManager>().getCachedDocument(file)).isSameAs(document)
    }
    finally {
      owner.cancel()
    }
  }
}
