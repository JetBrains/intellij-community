// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.diagnostic.PerformanceWatcher.Companion.printStacktrace
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AlarmTest {
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
  fun manyAlarmsDoNotStartTooManyThreads(@TestDisposable disposable: Disposable) {
    val used = ConcurrentCollectionFactory.createConcurrentSet<Thread>()
    val executed = AtomicInteger()
    val count = 100_000
    val alarms = Array(count) { Alarm(threadToUse = Alarm.ThreadToUse.POOLED_THREAD, disposable) }
    for (alarm in alarms) {
      alarm.addRequest(ContextAwareRunnable {
        executed.incrementAndGet()
        used.add(Thread.currentThread())
      }, 10)
    }

    for (alarm in alarms) {
      alarm.waitForAllExecuted(1, TimeUnit.SECONDS)
    }
    assertThat(used.size)
      .describedAs {
        "${used.size} threads created: ${used.joinToString { printStacktrace("", it, it.stackTrace) }}"
      }
      .isLessThanOrEqualTo(Runtime.getRuntime().availableProcessors() + 1 + 64 /* IO-pool thread is reused */)
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
    val alarm = Alarm(threadToUse = Alarm.ThreadToUse.SWING_THREAD, disposable)
    val list = ConcurrentLinkedQueue<String>()

    alarm.addRequest(ContextAwareRunnable { list.add("1") }, 0, ModalityState.nonModal())
    alarm.addRequest(ContextAwareRunnable { list.add("2") }, 5, ModalityState.nonModal())
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
  fun singleAlarmMustRefuseToInstantiateWithWrongModality() {
    assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
      SingleAlarm(task = {}, delay = 1, parentDisposable = null, threadToUse = Alarm.ThreadToUse.SWING_THREAD, modalityState = null)
    }
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
    expected.append(i).append(" ")
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
