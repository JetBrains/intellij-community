// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

@TestApplication
internal class AwaitFocusSettlesDownTest {
  @Test
  fun `the function resumes after the events that the event queue holds`(): Unit = timeoutRunBlocking {
    val processed = AtomicBoolean()
    SwingUtilities.invokeLater { processed.set(true) }

    withContext(Dispatchers.Default) {
      IdeFocusManager.getGlobalInstance().awaitFocusSettlesDown()
    }

    assertTrue(processed.get())
  }

  @Test
  fun `the function waits until the context modality state allows it`(): Unit = timeoutRunBlocking {
    val modalEntity = Any()
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      LaterInvocator.enterModal(modalEntity)
    }
    val job = launch(Dispatchers.Default + ModalityState.nonModal().asContextElement(), CoroutineStart.UNDISPATCHED) {
      IdeFocusManager.getGlobalInstance().awaitFocusSettlesDown()
    }
    try {
      awaitEventQueue()
      assertFalse(job.isCompleted)
    }
    finally {
      withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
        LaterInvocator.leaveModal(modalEntity)
      }
    }
    job.join()
  }
}

private suspend fun awaitEventQueue() {
  suspendCancellableCoroutine { continuation ->
    SwingUtilities.invokeLater { continuation.resume(Unit) }
  }
}
