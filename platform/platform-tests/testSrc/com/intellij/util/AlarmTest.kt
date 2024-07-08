// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.diagnostic.PerformanceWatcher.Companion.printStacktrace
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AlarmTest {
  @Test
  fun twoAddsWithZeroDelayMustExecuteSequentially(@TestDisposable disposable: Disposable) {
    val alarm = Alarm(disposable)
    assertRequestsExecuteSequentially(alarm)
  }

  @Test
  fun alarmRequestsShouldExecuteSequentiallyEvenInPooledThread(@TestDisposable disposable: Disposable) {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    assertRequestsExecuteSequentially(alarm)
  }

  @Test
  fun oneAlarmDoesNotStartTooManyThreads(@TestDisposable disposable: Disposable) {
    val alarm = Alarm(disposable)
    val executed = AtomicInteger()
    val count = 100000
    val used = ConcurrentCollectionFactory.createConcurrentSet<Thread>()
    for (i in 0 until count) {
      alarm.addRequest({
                         executed.incrementAndGet()
                         used.add(Thread.currentThread())
                       }, 10)
    }
    while (executed.get() != count) {
      UIUtil.dispatchAllInvocationEvents()
    }
    if (used.size > 10) {
      throw AssertionError(used.size.toString() + " threads created: " + used.joinToString { printStacktrace("", it, it.stackTrace) })
    }
  }

  @Test
  fun manyAlarmsDoNotStartTooManyThreads(@TestDisposable disposable: Disposable) {
    val used = ConcurrentCollectionFactory.createConcurrentSet<Thread>()
    val executed = AtomicInteger()
    val count = 100000
    val alarms = Array(count) { Alarm(disposable) }.toList()
    for (alarm in alarms) {
      alarm.addRequest({
                         executed.incrementAndGet()
                         used.add(Thread.currentThread())
                       }, 10)
    }

    while (executed.get() != count) {
      UIUtil.dispatchAllInvocationEvents()
    }
    if (used.size > 10) {
      throw AssertionError(used.size.toString() + " threads created: " + used.joinToString { printStacktrace("", it, it.stackTrace) })
    }
  }

  @Test
  fun testOrderIsPreservedAfterModalitySwitching() {
    val alarm = Alarm()
    val sb = StringBuilder()
    val modal = Any()
    LaterInvocator.enterModal(modal)

    try {
      ApplicationManager.getApplication().invokeLater({ TimeoutUtil.sleep(10) }, ModalityState.nonModal())
      alarm.addRequest({ sb.append("1") }, 0, ModalityState.nonModal())
      alarm.addRequest({ sb.append("2") }, 5, ModalityState.nonModal())
      UIUtil.dispatchAllInvocationEvents()
      org.junit.jupiter.api.Assertions.assertEquals("", sb.toString())
    }
    finally {
      LaterInvocator.leaveModal(modal)
    }

    while (!alarm.isEmpty) {
      UIUtil.dispatchAllInvocationEvents()
    }

    org.junit.jupiter.api.Assertions.assertEquals("12", sb.toString())
  }

  @Test
  fun flushImmediately() {
    val alarm = Alarm()
    val sb = StringBuilder()

    alarm.addRequest({ sb.append("1") }, 0, ModalityState.nonModal())
    alarm.addRequest({ sb.append("2") }, 5, ModalityState.nonModal())
    org.junit.jupiter.api.Assertions.assertEquals("", sb.toString())
    alarm.drainRequestsInTest()
    org.junit.jupiter.api.Assertions.assertEquals("12", sb.toString())
  }

  @Test
  fun waitForAllExecutedMustWaitUntilExecutionFinish(@TestDisposable disposable: Disposable) {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    val sb = StringBuffer()
    val start = System.currentTimeMillis()
    val delay = 100
    alarm.addRequest({
                       TimeoutUtil.sleep(1000)
                       sb.append("1")
                     }, delay)
    alarm.addRequest({
                       TimeoutUtil.sleep(1000)
                       sb.append("2")
                     }, delay * 2)

    val s = sb.toString()
    val elapsed = System.currentTimeMillis() - start
    if (elapsed > delay / 2) {
      System.err.println("No no no no this agent is so overloaded I quit")
      return
    }
    org.junit.jupiter.api.Assertions.assertEquals(2, alarm.activeRequestCount)
    org.junit.jupiter.api.Assertions.assertEquals("", s)
    try {
      // started to execute but not finished yet
      alarm.waitForAllExecuted(1000, TimeUnit.MILLISECONDS)
      fail()
    }
    catch (ignored: TimeoutException) {
    }

    alarm.waitForAllExecuted(3000, TimeUnit.MILLISECONDS)

    org.junit.jupiter.api.Assertions.assertEquals(2, sb.length)
  }

  @Test
  fun exceptionDuringAlarmExecutionMustManifestItselfInTests(@TestDisposable disposable: Disposable) {
    val alarm = Alarm(disposable)
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      alarm.addRequest({
                         throw RuntimeException("wtf")
                       }, 1)
      var caught = false
      while (!alarm.isEmpty) {
        try {
          UIUtil.dispatchAllInvocationEvents()
        }
        catch (e: RuntimeException) {
          caught = caught or ("wtf" == e.message)
        }
      }
      assertTrue(caught)
    }
    org.junit.jupiter.api.Assertions.assertEquals("wtf", error.message)
  }

  @Test
  fun singleAlarmMustRefuseToInstantiateWithWrongModality() {
    UsefulTestCase.assertThrows(IllegalArgumentException::class.java) {
      SingleAlarm(task = {}, delay = 1, parentDisposable = null, threadToUse = Alarm.ThreadToUse.SWING_THREAD, modalityState = null)
    }
  }
}

private fun assertRequestsExecuteSequentially(alarm: Alarm) {
  val count = 10000
  val log = StringBuffer(count * 4)
  val expected = StringBuilder(count * 4)

  for (i in 0 until count) {
    alarm.addRequest({ log.append(i).append(" ") }, 0)
  }
  for (i in 0 until count) {
    expected.append(i).append(" ")
  }
  val future = ApplicationManager.getApplication().executeOnPooledThread {
    try {
      alarm.waitForAllExecuted(100, TimeUnit.SECONDS)
    }
    catch (e: Exception) {
      throw RuntimeException(e)
    }
  }
  while (!future.isDone) {
    UIUtil.dispatchAllInvocationEvents()
  }
  future.get()
  Assertions.assertThat(alarm.isEmpty).isTrue()
  org.junit.jupiter.api.Assertions.assertEquals(expected.toString(), log.toString())
}
