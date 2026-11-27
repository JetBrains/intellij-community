// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Timer

private const val SCENARIO_DISABLED_REASON = "Run by JUnit5ParallelTestApplicationTest"
private const val PARALLELISM = 4

/**
 * Runs a nested engine with parallel execution on. A class with [TestApplication] is isolated, so no other test runs
 * at the same time as its tests. The classes without it overlap, and the check of leaked threads skips their tests.
 *
 * The outer class has the application too, so the nested engine shares it and does not dispose it.
 */
@TestApplication
class JUnit5ParallelTestApplicationTest {

  @Test
  fun `a class with the application runs alone in a parallel run`() {
    val summary = try {
      runConcurrently(
        PARALLELISM,
        FirstApplicationScenario::class.java,
        SecondApplicationScenario::class.java,
        FirstPlainScenario::class.java,
        SecondPlainScenario::class.java,
      )
    }
    finally {
      Tests.endTheLeakedThread()
    }

    assertNoFailures(summary)
    assertEquals(8L, summary.testsSucceededCount, failureReport(summary))
    assertEquals(1, Tests.mostAtOnceWithTheApplication.get())
  }

  @Test
  fun `a class that starts the application beside other tests fails`() {
    val summary = runConcurrently(PARALLELISM, ApplicationWithoutIsolationScenario::class.java, QuietScenario::class.java)

    assertEquals(1L, summary.totalFailureCount, failureReport(summary))
    assertThat(summary.failures.single().exception)
      .hasMessageContaining("starts the test application beside other tests of a parallel run")
  }

  @Test
  fun `a class with the application reports a Swing timer that a class beside other tests left`() {
    val summary = runConcurrently(PARALLELISM, TimerScenario::class.java, FirstApplicationScenario::class.java)

    assertEquals(1L, summary.totalFailureCount, failureReport(summary))
    assertThat(summary.failures.single().exception)
      .hasMessageStartingWith("A class that ran beside other tests of the parallel run left a Swing timer")
  }

  @TestApplication
  @Disabled(SCENARIO_DISABLED_REASON)
  class FirstApplicationScenario {
    @Test
    fun first(): Unit = Tests.runWithTheApplication()

    @Test
    fun second(): Unit = Tests.runWithTheApplication()

    @Test
    fun third(): Unit = Tests.runWithTheApplication()
  }

  @TestApplication
  @Disabled(SCENARIO_DISABLED_REASON)
  class SecondApplicationScenario {
    @Test
    fun first(): Unit = Tests.runWithTheApplication()

    @Test
    fun second(): Unit = Tests.runWithTheApplication()

    @Test
    fun third(): Unit = Tests.runWithTheApplication()
  }

  /**
   * The test waits for the test of [SecondPlainScenario]. A timeout on the barrier means that the run was sequential, and
   * that the scenario tested nothing.
   */
  @Disabled(SCENARIO_DISABLED_REASON)
  class FirstPlainScenario {
    @Test
    fun overlap(): Unit = Tests.overlap()
  }

  /**
   * The test also leaves a thread alive. The check of leaked threads skips a test that runs beside other tests, so the
   * thread fails no test.
   */
  @Disabled(SCENARIO_DISABLED_REASON)
  class SecondPlainScenario {
    @Test
    fun overlap() {
      Tests.leakAThread()
      Tests.overlap()
    }
  }

  /** The application extension without the isolation of [TestApplication]. */
  @ExtendWith(TestApplicationExtension::class)
  @Disabled(SCENARIO_DISABLED_REASON)
  class ApplicationWithoutIsolationScenario {
    @Test
    fun first() {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  class QuietScenario {
    @Test
    fun quiet() {
    }
  }

  /** The test leaves a running Swing timer. Its own check is skipped, because it runs beside other tests. */
  @Disabled(SCENARIO_DISABLED_REASON)
  class TimerScenario {
    @Test
    fun startsATimer() {
      Timer(TimeUnit.MINUTES.toMillis(1).toInt()) {}.start()
    }
  }

  private object Tests {
    private val running = AtomicInteger()
    val mostAtOnceWithTheApplication = AtomicInteger()
    private val barrier = CyclicBarrier(2)
    private val leakedThreadEnd = CountDownLatch(1)
    private var leakedThread: Thread? = null

    /** Counts every test that runs at the same time as a test of an application class, the test itself included. */
    fun runWithTheApplication() {
      mostAtOnceWithTheApplication.accumulateAndGet(running.incrementAndGet(), ::maxOf)
      try {
        // long enough for another test to start, if the isolation let it
        Thread.sleep(100)
      }
      finally {
        running.decrementAndGet()
      }
    }

    fun overlap() {
      running.incrementAndGet()
      try {
        barrier.await(1, TimeUnit.MINUTES)
      }
      finally {
        running.decrementAndGet()
      }
    }

    fun leakAThread() {
      leakedThread = Thread({ leakedThreadEnd.await() }, "a thread of a parallel test").also { it.start() }
    }

    fun endTheLeakedThread() {
      leakedThreadEnd.countDown()
      leakedThread?.join(TimeUnit.MINUTES.toMillis(1))
    }
  }
}
