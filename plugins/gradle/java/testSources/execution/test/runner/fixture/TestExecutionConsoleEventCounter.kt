// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.fixture

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TestExecutionConsoleEventCounter {

  private val suiteStartCounter = ConcurrentHashMap<SMTestProxy, AtomicInteger>()
  private val suiteFinishCounter = ConcurrentHashMap<SMTestProxy, AtomicInteger>()

  private val testStartCounter = ConcurrentHashMap<SMTestProxy, AtomicInteger>()
  private val testFinishCounter = ConcurrentHashMap<SMTestProxy, AtomicInteger>()
  private val testFailureCounter = ConcurrentHashMap<SMTestProxy, AtomicInteger>()
  private val testIgnoreCounter = ConcurrentHashMap<SMTestProxy, AtomicInteger>()

  fun install(project: Project, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable)
      .subscribe(SMTRunnerEventsListener.TEST_STATUS, object : SMTRunnerEventsAdapter() {
        override fun onSuiteStarted(suite: SMTestProxy) = onTestEvent(suiteStartCounter, suite)
        override fun onSuiteFinished(suite: SMTestProxy) = onTestEvent(suiteFinishCounter, suite)
        override fun onTestStarted(test: SMTestProxy) = onTestEvent(testStartCounter, test)
        override fun onTestFinished(test: SMTestProxy) = onTestEvent(testFinishCounter, test)
        override fun onTestFailed(test: SMTestProxy) = onTestEvent(testFailureCounter, test)
        override fun onTestIgnored(test: SMTestProxy) = onTestEvent(testIgnoreCounter, test)
      })
  }

  private fun onTestEvent(counters: ConcurrentHashMap<SMTestProxy, AtomicInteger>, testProxy: SMTestProxy) {
    val counter = counters.getOrPut(testProxy) {
      AtomicInteger(0)
    }
    counter.incrementAndGet()
  }

  fun assertTestEvents(
    name: String,
    suiteStart: Int,
    suiteFinish: Int,
    testStart: Int,
    testFinish: Int,
    testFailure: Int,
    testIgnore: Int
  ) {
    assertTestEvent(suiteStartCounter, name, suiteStart)
    assertTestEvent(suiteFinishCounter, name, suiteFinish)
    assertTestEvent(testStartCounter, name, testStart)
    assertTestEvent(testFinishCounter, name, testFinish)
    assertTestEvent(testFailureCounter, name, testFailure)
    assertTestEvent(testIgnoreCounter, name, testIgnore)
  }

  private fun assertTestEvent(counters: ConcurrentHashMap<SMTestProxy, AtomicInteger>, name: String, expected: Int) {
    val actual = counters
      .filter { it.key.name == name }
      .map { it.value.get() }
      .sum()
    Assertions.assertEquals(expected, actual) {
      "Failed to assert events for $name\n" +
      "Suite start $suiteStartCounter\n" +
      "Suite finish $suiteFinishCounter\n" +
      "Test start $testStartCounter\n" +
      "Test finish $testFinishCounter\n" +
      "Test failure $testFailureCounter\n" +
      "Test ignore $testIgnoreCounter\n"
    }
  }
}