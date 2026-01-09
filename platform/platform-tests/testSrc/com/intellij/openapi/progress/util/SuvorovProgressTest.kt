// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.locking.impl.NestedLocksThreadingSupport
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.awt.event.MouseEvent
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel

@TestApplication
class SuvorovProgressTest {

  @Test
  @RegistryKey("ide.suvorov.progress.showing.delay.ms", "1000")
  @RegistryKey("ide.suvorov.progress.kind", "[None|Bar*|Spinning|Overlay]")
  fun `input event gets dispatched if bar progress finishes quickly`() {
    Assumptions.assumeTrue { installSuvorovProgress }
    val clickPerformed = performClickDuringProgress()
    assertThat(clickPerformed).isTrue
  }

  @Test
  fun `input event gets dropped if progress is long`(@TestDisposable disposable: Disposable) {
    Assumptions.assumeTrue { installSuvorovProgress }
    Registry.get("ide.suvorov.progress.showing.delay.ms").setValue(1, disposable)
    Registry.get("ide.suvorov.progress.kind").setValue("[None|Bar*|Spinning|Overlay]", disposable)
    val clickPerformed = performClickDuringProgress()
    assertThat(clickPerformed).isFalse
  }

  private fun performClickDuringProgress(): /* is click performed */ Boolean = timeoutRunBlocking(context = Dispatchers.Default) {
    val writeActionMayFinish = Job(coroutineContext.job)
    val edtActionCompleted = Job(coroutineContext.job)
    launch {
      backgroundWriteAction {
        launch(Dispatchers.UiWithModelAccess) {
          WriteIntentReadAction.run {
            edtActionCompleted.complete()
          }
        }
        writeActionMayFinish.asCompletableFuture().join()
      }
    }
    delay(10)
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
    delay(10)
    IdeEventQueue.getInstance().postEvent(event)
    delay(10)
    // the event is waiting for execution
    assertThat(clickEventDispatched.get()).isFalse
    assertThat(edtActionCompleted.isCompleted).isFalse
    writeActionMayFinish.complete()
    edtActionCompleted.join()
    delay(10)
    withContext(Dispatchers.EDT) {}
    clickEventDispatched.get()
  }


  // this test must be correct regardless of the presence of Suvorov Progress
  @Test
  fun `input events are consumed in the order of sending`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val totalEventsCount = 100_000
    val countOfWriteActions = 100
    val countOfSuccessfulEvents = AtomicInteger()
    val orderedQueue = LinkedBlockingQueue<MouseEvent>()
    val panel = withContext(Dispatchers.EDT) {
      JPanel().apply {
        addMouseListener(object : java.awt.event.MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            val queueEvent = orderedQueue.remove()
            if (queueEvent === e) {
              countOfSuccessfulEvents.incrementAndGet()
            }
          }
        })
      }
    }

    val postingJob = launch {
      repeat(totalEventsCount) {
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
        orderedQueue.add(event)
        IdeEventQueue.getInstance().postEvent(event)
      }
    }
    withContext(Dispatchers.EDT) {
      repeat(countOfWriteActions) {
        val readActionJob = Job()
        val readActionMayFinish = Job()
        launch {
          readAction {
            readActionJob.complete()
            readActionMayFinish.asCompletableFuture().join()
          }
        }
        readActionJob.join()
        launch(Dispatchers.Default) {
          delay(20)
          readActionMayFinish.complete()
        }
        runWriteAction {
        }
      }
    }
    postingJob.join()
    withContext(Dispatchers.EDT) {}
    assertThat(countOfSuccessfulEvents.get()).isEqualTo(totalEventsCount)
  }

  @Test
  fun `suvorov progress is resilient to exceptions`(): Unit = timeoutRunBlocking {
    val lockingSupport = NestedLocksThreadingSupport()

    val waJob = Job(coroutineContext.job)
    launch(Dispatchers.Default) {
      lockingSupport.runWriteActionBlocking {
        waJob.asCompletableFuture().join()
      }
    }
    delay(10)
    val raJob = launch(Dispatchers.Default) {
      lockingSupport.setLockAcquisitionInterceptor {
        waJob.complete()
        Thread.sleep(10) // we let read permit to be acquired
        throw RuntimeException("test exception")
      }
      LoggedErrorProcessor.executeAndReturnLoggedError {
        lockingSupport.runReadAction {
        }
      }
    }
    delay(10)
    raJob.join()
    lockingSupport.runWriteActionBlocking {
    } // check that it can finish
  }
}