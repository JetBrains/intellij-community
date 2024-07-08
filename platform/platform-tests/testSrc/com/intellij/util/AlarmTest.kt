// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.diagnostic.PerformanceWatcher.Companion.printStacktrace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightPlatformTestCase.assertEquals
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

class AlarmTest : LightPlatformTestCase() {
  fun testTwoAddsWithZeroDelayMustExecuteSequentially() {
    val alarm = Alarm(testRootDisposable)
    assertRequestsExecuteSequentially(alarm)
  }

  fun testAlarmRequestsShouldExecuteSequentiallyEvenInPooledThread() {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, testRootDisposable)
    assertRequestsExecuteSequentially(alarm)
  }

  fun testOneAlarmDoesNotStartTooManyThreads() {
    val alarm = Alarm(testRootDisposable)
    val executed = AtomicInteger()
    val N = 100000
    val used = ConcurrentCollectionFactory.createConcurrentSet<Thread>()
    for (i in 0 until N) {
      alarm.addRequest({
                         executed.incrementAndGet()
                         used.add(Thread.currentThread())
                       }, 10)
    }
    while (executed.get() != N) {
      UIUtil.dispatchAllInvocationEvents()
    }
    if (used.size > 10) {
      fail(used.size.toString() + " threads created: " + used.joinToString { printStacktrace("", it, it.stackTrace) })
    }
  }

  fun testManyAlarmsDoNotStartTooManyThreads() {
    val used = ConcurrentCollectionFactory.createConcurrentSet<Thread>()
    val executed = AtomicInteger()
    val N = 100000
    val alarms = Stream.generate { Alarm(testRootDisposable) }.limit(N.toLong()).toList()
    alarms.forEach(java.util.function.Consumer { alarm: Alarm ->
      alarm.addRequest({
                         executed.incrementAndGet()
                         used.add(Thread.currentThread())
                       }, 10)
    })

    while (executed.get() != N) {
      UIUtil.dispatchAllInvocationEvents()
    }
    if (used.size > 10) {
      fail(used.size.toString() + " threads created: " + used.joinToString { printStacktrace("", it, it.stackTrace) })
    }
  }

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
      assertEquals("", sb.toString())
    }
    finally {
      LaterInvocator.leaveModal(modal)
    }

    while (!alarm.isEmpty) {
      UIUtil.dispatchAllInvocationEvents()
    }

    assertEquals("12", sb.toString())
  }

  fun testFlushImmediately() {
    val alarm = Alarm()
    val sb = StringBuilder()

    alarm.addRequest({ sb.append("1") }, 0, ModalityState.nonModal())
    alarm.addRequest({ sb.append("2") }, 5, ModalityState.nonModal())
    assertEquals("", sb.toString())
    alarm.drainRequestsInTest()
    assertEquals("12", sb.toString())
  }

  @Throws(Exception::class)
  fun testWaitForAllExecutedMustWaitUntilExecutionFinish() {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, testRootDisposable)
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
    assertEquals(2, alarm.activeRequestCount)
    assertEquals("", s)
    try {
      // started to execute but not finished yet
      alarm.waitForAllExecuted(1000, TimeUnit.MILLISECONDS)
      fail()
    }
    catch (ignored: TimeoutException) {
    }

    alarm.waitForAllExecuted(3000, TimeUnit.MILLISECONDS)

    assertEquals(2, sb.length)
  }

  fun testExceptionDuringAlarmExecutionMustManifestItselfInTests() {
    val alarm = Alarm(testRootDisposable)
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
    assertEquals("wtf", error.message)
  }

  fun testSingleAlarmMustRefuseToInstantiateWithWrongModality() {
    UsefulTestCase.assertThrows(IllegalArgumentException::class.java) {
      SingleAlarm(
        {}, 1, null, Alarm.ThreadToUse.SWING_THREAD, null)
    }
  }
}

private fun assertRequestsExecuteSequentially(alarm: Alarm) {
  val N = 10000
  val log = StringBuffer(N * 4)
  val expected = StringBuilder(N * 4)

  for (i in 0 until N) {
    val finalI = i
    alarm.addRequest({ log.append(finalI).append(" ") }, 0)
  }
  for (i in 0 until N) {
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
  assertEquals(expected.toString(), log.toString())
}
