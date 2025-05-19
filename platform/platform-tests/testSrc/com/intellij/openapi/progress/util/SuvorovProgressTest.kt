// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.impl.getGlobalThreadingSupport
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel

@TestApplication
class SuvorovProgressTest {

  @BeforeEach
  fun installSuvorovProgress() {
    application.invokeAndWait {
      getGlobalThreadingSupport().setLockAcquisitionInterceptor(SuvorovProgress::dispatchEventsUntilComputationCompletes)
    }
  }

  @AfterEach
  fun removeSuvorovProgress() {
    application.invokeAndWait {
      getGlobalThreadingSupport().removeLockAcquisitionInterceptor()
    }
  }

  @Test
  @RegistryKey("ide.suvorov.progress.showing.delay.ms", "1000")
  @RegistryKey("ide.suvorov.progress.kind", "[None|Bar*|Spinning|Overlay]")
  fun `input event gets dispatched if bar progress finishes quickly`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val clickPerformed = performClickDuringProgress()
    assertThat(clickPerformed).isTrue
  }

  @Test
  @RegistryKey("ide.suvorov.progress.showing.delay.ms", "1")
  @RegistryKey("ide.suvorov.progress.kind", "[None|Bar*|Spinning|Overlay]")
  fun `input event gets dropped if progress is long`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val clickPerformed = performClickDuringProgress()
    assertThat(clickPerformed).isFalse
  }

  private fun performClickDuringProgress(): /* is click performed */ Boolean = timeoutRunBlocking(context = Dispatchers.Default) {
    val writeActionMayFinish = Job(coroutineContext.job)
    val edtActionCompleted = Job(coroutineContext.job)
    launch {
      backgroundWriteAction {
        launch(Dispatchers.EDT) {
          edtActionCompleted.complete()
        }
        writeActionMayFinish.asCompletableFuture().join()
      }
    }
    delay(100)
    val clickEventDispatched = AtomicBoolean(false)
    val panel = JPanel().apply {
      addMouseListener(object : java.awt.event.MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          clickEventDispatched.set(true)
        }
      })
    }
    val event = MouseEvent(
      panel,
      MouseEvent.MOUSE_CLICKED,
      System.currentTimeMillis(),
      0,
      0,
      0,
      1,
      false
    )
    delay(100)
    IdeEventQueue.getInstance().postEvent(event)
    delay(100)
    // the event is waiting for execution
    assertThat(clickEventDispatched.get()).isFalse
    assertThat(edtActionCompleted.isCompleted).isFalse
    writeActionMayFinish.complete()
    edtActionCompleted.join()
    delay(100)
    clickEventDispatched.get()
  }
}