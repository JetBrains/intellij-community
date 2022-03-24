// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.progress.timeoutRunBlocking
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.UncaughtExceptionsExtension
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

class EdtCoroutineDispatcherTest {

  companion object {

    @RegisterExtension
    @JvmField
    val applicationExtension = ApplicationExtension()
  }

  @RegisterExtension
  @JvmField
  val uncaughtExceptionsExtension = UncaughtExceptionsExtension()

  @AfterEach
  fun cleanEDTQueue() {
    UIUtil.pump()
  }

  @Test
  fun `dispatch thread`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      ApplicationManager.getApplication().assertIsDispatchThread()
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
    assertSame(t, thrown)
  }

  @Test
  fun `cancelled coroutine finishes normally ignoring modality and does not leak`() {
    val leak = object : Any() {
      override fun toString(): String = "leak"
    }
    val root = LaterInvocator::class.java

    ApplicationManager.getApplication().withModality {
      val job = CoroutineScope(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()).launch {
        fail(leak.toString()) // keep reference to the leak
      }
      assertReferenced(root, leak)
      timeoutRunBlocking {
        job.cancelAndJoin()
      }
      LeakHunter.checkLeak(root, leak.javaClass)
    }
  }
}
