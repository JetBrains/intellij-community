// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
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
import java.util.function.Supplier

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
    val leakClass = leak.javaClass
    val root = LaterInvocator::class.java

    fun assertLeakIsReferenced() {
      lateinit var foundLeak: Any
      val rootSupplier: Supplier<Map<Any, String>> = Supplier {
        mapOf(root to "root")
      }
      LeakHunter.processLeaks(rootSupplier, leakClass, null) { leaked, _ ->
        foundLeak = leaked
        false
      }
      assertSame(leak, foundLeak)
    }

    ApplicationManager.getApplication().withModality {
      val job = CoroutineScope(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()).launch {
        fail(leak.toString()) // keep reference to the leak
      }
      assertLeakIsReferenced()
      timeoutRunBlocking {
        job.cancelAndJoin()
      }
      LeakHunter.checkLeak(root, leakClass)
    }
  }

  private fun Application.withModality(action: () -> Unit) {
    val modalEntity = Any()
    invokeAndWait(Runnable {
      LaterInvocator.enterModal(modalEntity)
    }, ModalityState.any())
    try {
      action()
    }
    finally {
      invokeAndWait(Runnable {
        LaterInvocator.leaveModal(modalEntity)
      }, ModalityState.any())
    }
  }
}
