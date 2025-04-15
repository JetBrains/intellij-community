// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class AlarmTest {
  @Test
  fun `isDisposed is true if parentDisposable used`() {
    val disposable = Disposer.newDisposable()
    disposable.use {
      @Suppress("DEPRECATION")
      val alarm = SingleAlarm.pooledThreadSingleAlarm(delay = 100, parentDisposable = disposable) { }
      Disposer.dispose(disposable)
      assertThat(alarm.isDisposed).isTrue()
    }
  }

  @Test
  fun `SingleAlarm with parent disposable must ignore request after disposal`() {
    val disposable = Disposer.newDisposable()
    disposable.use {
      val counter = AtomicInteger()

      @Suppress("DEPRECATION")
      val alarm = SingleAlarm.pooledThreadSingleAlarm(delay = 1, parentDisposable = disposable) {
        counter.incrementAndGet()
      }

      alarm.request()
      alarm.waitForAllExecuted(1, TimeUnit.SECONDS)
      assertThat(counter.get()).isEqualTo(1)
      Disposer.dispose(disposable)

      alarm.request()
      alarm.waitForAllExecuted(1, TimeUnit.SECONDS)
      assertThat(counter.get()).isEqualTo(1)
    }
  }

  @Test
  fun `cancel request by task`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.EDT) {
    val alarm = Alarm(threadToUse = Alarm.ThreadToUse.SWING_THREAD, parentDisposable = disposable)
    val list = ConcurrentLinkedQueue<String>()
    val r1 = Runnable { list.add("1") }
    val r2 = Runnable { list.add("2") }
    alarm.addRequest(request = r1, delayMillis = 100, modalityState = ModalityState.nonModal())
    alarm.addRequest(request = r2, delayMillis = 200, modalityState = ModalityState.nonModal())
    assertThat(list).isEmpty()
    alarm.cancelRequest(r1)
    alarm.drainRequestsInTest()
    assertThat(list).containsExactly("2")
  }

  @Test
  fun alarmRequestsShouldExecuteSequentiallyInEdt(@TestDisposable disposable: Disposable) {
    assertRequestsExecuteSequentially(Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable))
  }

  @Test
  fun alarmRequestsShouldExecuteSequentiallyInPooledThread(@TestDisposable disposable: Disposable) {
    assertRequestsExecuteSequentially(Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable))
  }

  @Test
  fun `alarm with short delay executed first`(@TestDisposable disposable: Disposable) {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    val list = ConcurrentLinkedQueue<String>()
    alarm.addRequest(ContextAwareRunnable { list.add("B") }, 10)
    alarm.addRequest(ContextAwareRunnable { list.add("A") }, 1)
    alarm.waitForAllExecuted(20, TimeUnit.MILLISECONDS)
    assertThat(list).containsExactly("A", "B")
  }

  @Test
  fun orderIsPreservedAfterModalitySwitching(): Unit = runBlocking(Dispatchers.EDT) {
    val alarm = Alarm()
    val sb = StringBuilder()
    val modal = Any()
    LaterInvocator.enterModal(modal)

    try {
      ApplicationManager.getApplication().invokeLater({ TimeoutUtil.sleep(10) }, ModalityState.nonModal())
      alarm.addRequest({ sb.append("1") }, 0, ModalityState.nonModal())
      alarm.addRequest({ sb.append("2") }, 5, ModalityState.nonModal())
      UIUtil.dispatchAllInvocationEvents()
      assertThat(sb).isEmpty()
    }
    finally {
      LaterInvocator.leaveModal(modal)
    }

    while (!alarm.isEmpty) {
      UIUtil.dispatchAllInvocationEvents()
    }

    assertThat(sb.toString()).isEqualTo("12")
  }

  @Test
  fun flushImmediately(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.EDT) {
    val alarm = Alarm(threadToUse = Alarm.ThreadToUse.SWING_THREAD, parentDisposable = disposable)
    val list = ConcurrentLinkedQueue<String>()

    alarm.addRequest(request = { list.add("1") }, delayMillis = 0, modalityState = ModalityState.nonModal())
    alarm.addRequest(request = { list.add("2") }, delayMillis = 5, modalityState = ModalityState.nonModal())
    assertThat(list).isEmpty()
    alarm.drainRequestsInTest()
    assertThat(list).containsExactly("1", "2")
  }

  @Test
  fun waitForAllExecutedMustWaitUntilExecutionFinish(@TestDisposable disposable: Disposable) {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    val sb = StringBuffer()
    val start = System.currentTimeMillis()
    val delay = 100
    alarm.addRequest(ContextAwareRunnable {
      TimeoutUtil.sleep(1000)
      sb.append('1')
    }, delay)
    alarm.addRequest(ContextAwareRunnable {
      TimeoutUtil.sleep(1000)
      sb.append('2')
    }, delay * 2)

    val s = sb.toString()
    val elapsed = System.currentTimeMillis() - start
    if (elapsed > delay / 2) {
      System.err.println("No no no no this agent is so overloaded I quit")
      return
    }

    assertThat(alarm.activeRequestCount).isEqualTo(2)
    assertThat(s).isEmpty()
    try {
      // started to execute but not finished yet
      alarm.waitForAllExecuted(1000, TimeUnit.MILLISECONDS)
      fail()
    }
    catch (ignored: TimeoutException) {
    }

    alarm.waitForAllExecuted(3000, TimeUnit.MILLISECONDS)

    assertThat(sb).hasSize(2)
  }

  @Test
  fun exceptionDuringAlarmExecutionMustManifestItselfInTests(@TestDisposable disposable: Disposable) {
    val alarm = Alarm(threadToUse = Alarm.ThreadToUse.POOLED_THREAD, disposable)
    val errorMessage = "_catch_me_"
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      alarm.addRequest(ContextAwareRunnable { throw RuntimeException(errorMessage) }, 1)
      alarm.waitForAllExecuted(1000, TimeUnit.MILLISECONDS)
    }
    assertThat(error).hasMessage(errorMessage)
  }

  @Test
  fun `all canceled tasks are awaited`() = timeoutRunBlocking {
    val counter = AtomicInteger()
    val alarm = SingleAlarm(
      {
        assertEquals(1, counter.incrementAndGet())
        try {
          Thread.sleep(100)
        } finally {
          assertEquals(0, counter.decrementAndGet())
        }
      }, 10, null, Alarm.ThreadToUse.POOLED_THREAD, null, null)
    repeat(10) {
      alarm.cancelAndRequest(false)
      delay(20.milliseconds)
    }
  }

  @Test
  fun `all canceled tasks are awaited 2`() = timeoutRunBlocking {
    val counter = AtomicInteger()
    val alarm = SingleAlarm(
      {
        assertEquals(1, counter.incrementAndGet())
        try {
          Thread.sleep(100)
        } finally {
          assertEquals(0, counter.decrementAndGet())
        }
      }, 10, null, Alarm.ThreadToUse.POOLED_THREAD, null, null)
    delay(50)
    alarm.cancelAndRequest(false)
    alarm.cancelAndRequest(false)
    delay(50)
    alarm.cancelAndRequest(false)
    alarm.cancelAndRequest(false)
  }
}

private fun assertRequestsExecuteSequentially(alarm: Alarm) {
  val count = 10_000
  val log = StringBuffer(count * 4)
  val expected = StringBuilder(count * 4)

  for (i in 0 until count) {
    alarm.addRequest(ContextAwareRunnable { log.append(i).append(' ') }, 0)
  }
  for (i in 0 until count) {
    expected.append(i).append(' ')
  }
  @Suppress("OPT_IN_USAGE")
  val future = GlobalScope.async {
    alarm.waitForAllExecuted(100, TimeUnit.SECONDS)
  }

  runBlocking(Dispatchers.EDT) {
    while (!future.isCompleted) {
      UIUtil.dispatchAllInvocationEvents()
    }
  }

  future.asCompletableFuture().join()
  assertThat(alarm.isEmpty).isTrue()
  assertThat(log.toString()).isEqualTo(expected.toString())
}
