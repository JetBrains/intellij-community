// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.elf.ElfFeatureFlag
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.EDT
import org.junit.jupiter.api.Test
import java.awt.event.InvocationEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@TestApplication
class ElfDocumentSyncSchedulerTest {
  @Test
  fun `test one elf scope schedules action on EDT with write action`() {
    withLockFreeTyping {
      val runCount = AtomicInteger()
      val scheduler = object : ElfDocumentSyncScheduler() {
        override fun sync() {
          assertTrue(EDT.isCurrentThreadEdt())
          assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed)
          assertFalse(Elf.getElf().isInElfScope())
          runCount.incrementAndGet()
        }
      }
      withElfScope {
        scheduler.schedule()
        waitForElfSchedulerIdle()
        assertEquals(0, runCount.get())
      }
      dispatchEventsUntilCondition(
        condition = { runCount.get() == 1 },
        errorMessage = { "Scheduled ELF document sync action was not executed" },
      )
    }
  }

  @Test
  fun `test outside elf scope schedules action on EDT with write action`() {
    withLockFreeTyping {
      val runCount = AtomicInteger()
      val scheduler = object : ElfDocumentSyncScheduler() {
        override fun sync() {
          assertTrue(EDT.isCurrentThreadEdt())
          assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed)
          assertFalse(Elf.getElf().isInElfScope())
          runCount.incrementAndGet()
        }
      }
      scheduler.schedule()
      dispatchEventsUntilCondition(
        condition = { runCount.get() == 1 },
        errorMessage = { "Scheduled ELF document sync action was not executed" },
      )
    }
  }

  @Test
  fun `test scheduled action is rescheduled when it reaches EDT inside elf scope`() {
    withLockFreeTyping {
      val runCount = AtomicInteger()
      val scheduler = object : ElfDocumentSyncScheduler() {
        override fun sync() {
          assertTrue(EDT.isCurrentThreadEdt())
          assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed)
          assertFalse(Elf.getElf().isInElfScope())
          runCount.incrementAndGet()
        }
      }
      scheduler.schedule()
      withElfScope {
        waitForElfSchedulerIdle()
        assertEquals(0, runCount.get())
      }
      dispatchEventsUntilCondition(
        condition = { runCount.get() == 1 },
        errorMessage = { "Scheduled ELF document sync action was not rescheduled after ELF scope" },
      )
    }
  }

  @Test
  fun `test contended elf scopes are coalesced into one scheduled action`() {
    withLockFreeTyping {
      val runCount = AtomicInteger()
      val scheduler = object : ElfDocumentSyncScheduler() {
        override fun sync() {
          runCount.incrementAndGet()
        }
      }
      withElfScope {
        scheduler.schedule()
      }
      withElfScope {
        scheduler.schedule()
      }
      waitForElfSchedulerIdle()
      assertEquals(1, runCount.get())
    }
  }

  private fun waitForElfSchedulerIdle() {
    val done = AtomicBoolean(false)
    ElfDocumentSyncScheduler.invokeLaterWithWriteAccess {
      done.set(true)
    }
    dispatchEventsUntilCondition(
      condition = { done.get() },
      errorMessage = { "ELF scheduler did not become idle" },
    )
  }

  private fun withLockFreeTyping(action: () -> Unit): Unit = timeoutRunBlocking {
    writeIntentReadAction {
      ElfFeatureFlag.withEnabled(action)
    }
  }

  private fun withElfScope(action: () -> Unit) {
    Elf.getElf().withElfScope {
      action.invoke()
    }
  }

  private fun dispatchEventsUntilCondition(condition: () -> Boolean, errorMessage: () -> String) {
    if (condition()) {
      return
    }
    TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
      dispatchEventsUntilConditionNoWriteIntent(condition, errorMessage)
    }
  }

  private fun dispatchEventsUntilConditionNoWriteIntent(condition: () -> Boolean, errorMessage: () -> String) {
    if (condition()) {
      return
    }
    val eventQueue = IdeEventQueue.getInstance()
    val timedOut = AtomicBoolean(false)
    val timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule(
      { eventQueue.postEvent(InvocationEvent(eventQueue) { timedOut.set(true) }) },
      DEADLOCK_TIMEOUT_SECONDS,
      TimeUnit.SECONDS,
    )
    try {
      while (!condition()) {
        eventQueue.dispatchEvent(eventQueue.nextEvent)
        if (timedOut.get() && !condition()) {
          fail(errorMessage())
        }
      }
    } finally {
      timeout.cancel(false)
    }
  }

  companion object {
    private const val DEADLOCK_TIMEOUT_SECONDS: Long = 120
  }
}
