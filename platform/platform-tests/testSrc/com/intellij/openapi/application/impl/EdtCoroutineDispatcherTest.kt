// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
class EdtCoroutineDispatcherTest {

  @BeforeEach
  fun cleanEDTQueue() {
    UIUtil.pump()
  }

  @Test
  fun `dispatch thread`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      ThreadingAssertions.assertEventDispatchThread()
    }
  }

  @Test
  fun `externally cancelled coroutine`(): Unit = timeoutRunBlocking {
    val edtJob = launch(Dispatchers.EDT) {
      delay(Long.MAX_VALUE)
    }
    edtJob.cancel()
  }

  @Test
  fun `internally cancelled coroutine`(): Unit = timeoutRunBlocking {
    assertThrows<CancellationException> {
      withContext(Job() + Dispatchers.EDT) {
        throw CancellationException()
      }
    }
  }

  @Test
  fun `failed coroutine`(): Unit = timeoutRunBlocking {
    val t = object : Throwable() {}
    val thrown = assertThrows<Throwable> {
      withContext(Job() + Dispatchers.EDT) {
        throw t
      }
    }
    //suppressed until this one is fixed: https://youtrack.jetbrains.com/issue/KT-52379
    @Suppress("AssertBetweenInconvertibleTypes")
    assertSame(t, thrown)
  }

  @Test
  fun `cancelled coroutine finishes normally ignoring modality and does not leak`() {
    val leak = object : Any() {
      override fun toString(): String = "leak"
    }
    val root = LaterInvocator::class.java

    ApplicationManager.getApplication().withModality {
      @OptIn(DelicateCoroutinesApi::class)
      val job = GlobalScope.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
        fail(leak.toString()) // keep reference to the leak
      }
      assertReferenced(root, leak)
      timeoutRunBlocking {
        job.cancelAndJoin()
      }
      LeakHunter.checkLeak(root, leak.javaClass)
    }
  }

  @Test
  fun `dispatched runnable does not leak through uncompleted coroutine`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val job = launch {
        // this part is wrapped into DispatchedRunnable
        awaitCancellation()
        // this part is also wrapped into DispatchedRunnable
      }
      yield() // checkLeak should be executed after the coroutine suspends in awaitCancellation
      @Suppress("INVISIBLE_REFERENCE")
      val leakClass = DispatchedRunnable::class.java
      LeakHunter.checkLeak(job, leakClass)
      job.cancel()
    }
  }

  @Test
  fun `switch to EDT under read lock fails with ISE`(): Unit = timeoutRunBlocking {
    readAction {
      assertThrows<IllegalStateException> {
        runBlockingMaybeCancellable {
          launch(Dispatchers.Default) {
            withContext(Dispatchers.EDT) {
              fail<Nothing>()
            }
          }
        }
      }
    }
  }
}
