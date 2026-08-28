// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.impl.TestUncaughtExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.engine.Constants
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

private const val SCENARIO_DISABLED_REASON = "Run by JUnit5ParallelUncaughtExceptionTest"
private const val PARALLELISM = 4
private const val BACKGROUND_FAILURE_MARKER = "a background thread of a concurrent test"

/**
 * Runs a nested engine with parallel execution on, because the outer run is sequential.
 *
 * @see com.intellij.testFramework.junit5.impl.UncaughtExceptionExtension
 */
class JUnit5ParallelUncaughtExceptionTest {

  @Test
  fun `concurrent tests keep the uncaught exception handler`() {
    val summary = runConcurrently(ConcurrentScenario::class.java)

    assertNoFailures(summary)
    assertEquals(PARALLELISM.toLong(), summary.testsSucceededCount)
  }

  @Test
  fun `one exception on a thread fails one test of a concurrent run`() {
    val summary = runConcurrently(BackgroundFailureScenario::class.java)

    assertEquals(1L, summary.totalFailureCount, failureReport(summary))
    assertEquals(PARALLELISM.toLong() - 1, summary.testsSucceededCount, failureReport(summary))
    assertThat(summary.failures.single().exception).hasMessageContaining(BACKGROUND_FAILURE_MARKER)
  }

  /**
   * The tests of the scenario overlap. A timeout on the barrier means that the run was sequential,
   * and that the scenario tested nothing.
   */
  @Disabled(SCENARIO_DISABLED_REASON)
  class ConcurrentScenario {

    @Test
    fun first(): Unit = overlap()

    @Test
    fun second(): Unit = overlap()

    @Test
    fun third(): Unit = overlap()

    @Test
    fun fourth(): Unit = overlap()

    private fun overlap() {
      assertInstanceOf(TestUncaughtExceptionHandler::class.java, Thread.getDefaultUncaughtExceptionHandler())
      barrier.await(1, TimeUnit.MINUTES)
    }

    companion object {
      private val barrier = CyclicBarrier(PARALLELISM)
    }
  }

  /**
   * One test ends a thread with an exception. The three other tests show that the run reports the
   * exception one time, and not one time for every test that runs at the same time.
   */
  @Disabled(SCENARIO_DISABLED_REASON)
  class BackgroundFailureScenario {

    @Test
    fun throwsOnAThread() {
      val thread = Thread({ throw IllegalStateException(BACKGROUND_FAILURE_MARKER) }, "uncaught-exception-scenario")
      thread.start()
      // The JVM calls the handler before the thread dies, so the exception is in the handler here.
      thread.join()
    }

    @Test
    fun quietFirst() {
    }

    @Test
    fun quietSecond() {
    }

    @Test
    fun quietThird() {
    }
  }
}

private fun runConcurrently(scenarioClass: Class<*>): TestExecutionSummary {
  // The auto-registered session listeners of the outer run enable IDE-wide extensions a second
  // time. The nested run needs only the extensions that META-INF/services declares.
  val launcher = LauncherFactory.create(
    LauncherConfig.builder()
      .enableLauncherSessionListenerAutoRegistration(false)
      .build()
  )
  val request = LauncherDiscoveryRequestBuilder.request()
    .selectors(selectClass(scenarioClass))
    .configurationParameter("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
    .configurationParameter(Constants.EXTENSIONS_AUTODETECTION_ENABLED_PROPERTY_NAME, "true")
    .configurationParameter(Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, "true")
    .configurationParameter(Constants.DEFAULT_PARALLEL_EXECUTION_MODE, "concurrent")
    .configurationParameter(Constants.PARALLEL_CONFIG_STRATEGY_PROPERTY_NAME, "fixed")
    .configurationParameter(Constants.PARALLEL_CONFIG_FIXED_PARALLELISM_PROPERTY_NAME, PARALLELISM.toString())
    .build()

  val listener = SummaryGeneratingListener()
  launcher.execute(request, listener)
  return listener.summary
}

private fun assertNoFailures(summary: TestExecutionSummary) {
  if (summary.totalFailureCount == 0L) return
  fail(failureReport(summary))
}

private fun failureReport(summary: TestExecutionSummary): String {
  val report = StringWriter()
  PrintWriter(report).use { writer ->
    summary.printFailuresTo(writer, 30)
  }
  return report.toString()
}
