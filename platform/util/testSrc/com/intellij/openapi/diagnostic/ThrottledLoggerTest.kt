// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class ThrottledLoggerTest {

  /**
   * Critical test: Verifies that when multiple coroutines call logging methods simultaneously,
   * exactly one message is logged (no duplicates due to race conditions).
   */
  @Test
  fun testConcurrentLoggingNoDuplicates() = timeoutRunBlocking(context = Dispatchers.Default) {
    val testLogger = CountingTestLogger()
    val throttledLogger = ThrottledLogger(testLogger, 1000)

    // Launch 10 coroutines that will all try to log simultaneously
    val jobs = List(10) {
      launch(Dispatchers.Default) {
        throttledLogger.info("concurrent message")
      }
    }

    // Wait for all to complete
    jobs.joinAll()

    // CRITICAL: Exactly 1 call, not 2+ (proves race condition is fixed)
    assertEquals(1, testLogger.infoCount.get(),
                 "Expected exactly 1 log message, but got ${testLogger.infoCount.get()}")
  }

  /**
   * Verifies that Supplier is NOT evaluated when message is throttled.
   * This is both a performance optimization and correct semantics.
   */
  @Test
  fun testSupplierNotEvaluatedWhenThrottled() {
    val testLogger = CountingTestLogger()
    val throttledLogger = ThrottledLogger(testLogger, 100)

    val supplierCallCount = AtomicInteger(0)

    // First call: should log and evaluate supplier
    throttledLogger.info {
      supplierCallCount.incrementAndGet()
      "message 1"
    }
    assertEquals(1, supplierCallCount.get(), "Supplier should be evaluated on first call")
    assertEquals(1, testLogger.infoCount.get(), "First message should be logged")

    // Second call immediately: should be throttled, supplier NOT evaluated
    throttledLogger.info {
      supplierCallCount.incrementAndGet()
      "message 2"
    }
    assertEquals(1, supplierCallCount.get(), "Supplier should NOT be evaluated when throttled")
    assertEquals(1, testLogger.infoCount.get(), "Second message should be throttled")
  }

  /**
   * Tests basic throttling behavior: first message logs, subsequent messages within
   * throttle period are suppressed, messages after period logs again.
   */
  @Test
  fun testBasicThrottlingBehavior() = timeoutRunBlocking(context = Dispatchers.Default) {
    val testLogger = CountingTestLogger()
    val throttledLogger = ThrottledLogger(testLogger, 50)  // 50ms throttle

    // First call: should log
    throttledLogger.warn("message 1")
    assertEquals(1, testLogger.warnCount.get())

    // Immediate second call: should be throttled
    throttledLogger.warn("message 2")
    assertEquals(1, testLogger.warnCount.get())

    // Wait for throttle period to expire
    delay(60.milliseconds)

    // Third call after period: should log
    throttledLogger.warn("message 3")
    assertEquals(2, testLogger.warnCount.get())
  }

  /**
   * Stress test with high contention: 50 coroutines making 20 calls each.
   * Verifies bounded behavior and correctness under extreme load.
   */
  @Test
  fun testHighContentionStress() = timeoutRunBlocking(context = Dispatchers.Default) {
    val testLogger = CountingTestLogger()
    val throttledLogger = ThrottledLogger(testLogger, 100)

    val coroutineCount = 50
    val callsPerCoroutine = 20

    val jobs = List(coroutineCount) {
      launch(Dispatchers.Default) {
        repeat(callsPerCoroutine) {
          throttledLogger.error("stress test message")
        }
      }
    }

    jobs.joinAll()

    // Should be very few messages logged (throttled aggressively)
    val loggedCount = testLogger.errorCount.get()
    assertTrue(loggedCount > 0, "At least one message should be logged")
    assertTrue(loggedCount < coroutineCount * callsPerCoroutine / 10,
               "Most messages should be throttled, but got $loggedCount")
  }

  /**
   * Tests all log levels (debug, info, warn, error) to ensure CAS logic works for each.
   */
  @Test
  fun testAllLogLevels() = timeoutRunBlocking(context = Dispatchers.Default) {
    val testLogger = CountingTestLogger()
    val throttledLogger = ThrottledLogger(testLogger, 50)

    // Test debug level
    throttledLogger.debug("debug 1")
    throttledLogger.debug("debug 2")  // throttled
    assertEquals(1, testLogger.debugCount.get())

    delay(60.milliseconds)

    // Test info level
    throttledLogger.info("info 1")
    throttledLogger.info("info 2")  // throttled
    assertEquals(1, testLogger.infoCount.get())

    delay(60.milliseconds)

    // Test warn level
    throttledLogger.warn("warn 1")
    throttledLogger.warn("warn 2")  // throttled
    assertEquals(1, testLogger.warnCount.get())

    delay(60.milliseconds)

    // Test error level
    throttledLogger.error("error 1")
    throttledLogger.error("error 2")  // throttled
    assertEquals(1, testLogger.errorCount.get())
  }

  /**
   * Tests throttling with Throwable parameter.
   */
  @Test
  fun testThrottlingWithThrowable() {
    val testLogger = CountingTestLogger()
    val throttledLogger = ThrottledLogger(testLogger, 100)

    val ex = Exception("test exception")

    throttledLogger.error("error with throwable 1", ex)
    assertEquals(1, testLogger.errorCount.get())

    throttledLogger.error("error with throwable 2", ex)  // throttled
    assertEquals(1, testLogger.errorCount.get())
  }

  /**
   * Tests that wrappedLogger() returns the original logger.
   */
  @Test
  fun testWrappedLogger() {
    val testLogger = CountingTestLogger()
    val throttledLogger = ThrottledLogger(testLogger, 100)

    assertSame(testLogger, throttledLogger.wrappedLogger())
  }

  /**
   * Tests constructor validation.
   */
  @Test
  fun testConstructorValidation() {
    val testLogger = CountingTestLogger()

    // Negative throttle should throw
    assertThrows(IllegalArgumentException::class.java) {
      ThrottledLogger(testLogger, -1)
    }

    // Zero values should be throw
    assertThrows(IllegalArgumentException::class.java) {
      ThrottledLogger(testLogger, 0)
    }

    // Positive values should be valid
    assertDoesNotThrow {
      ThrottledLogger(testLogger, 1000)
    }
  }

  /**
   * Tests that debug respects logger.isDebugEnabled()
   */
  @Test
  fun testDebugEnabledCheck() {
    val testLogger = CountingTestLogger(debugEnabled = false)
    val throttledLogger = ThrottledLogger(testLogger, 100)

    throttledLogger.debug("should not log")
    assertEquals(0, testLogger.debugCount.get(), "Debug should not log when disabled")

    testLogger.debugEnabled = true
    throttledLogger.debug("should log")
    assertEquals(1, testLogger.debugCount.get(), "Debug should log when enabled")
  }

  /**
   * Test logger that counts calls to each log level.
   */
  private class CountingTestLogger(var debugEnabled: Boolean = true) : Logger() {
    val debugCount = AtomicInteger(0)
    val infoCount = AtomicInteger(0)
    val warnCount = AtomicInteger(0)
    val errorCount = AtomicInteger(0)

    override fun isDebugEnabled(): Boolean = debugEnabled

    override fun debug(message: String?) {
      debugCount.incrementAndGet()
    }

    override fun debug(t: Throwable?) {
      debugCount.incrementAndGet()
    }

    override fun debug(message: String?, t: Throwable?) {
      debugCount.incrementAndGet()
    }

    override fun info(message: String?) {
      infoCount.incrementAndGet()
    }

    override fun info(message: String?, t: Throwable?) {
      infoCount.incrementAndGet()
    }

    override fun warn(message: String?, t: Throwable?) {
      warnCount.incrementAndGet()
    }

    override fun error(message: String?, t: Throwable?, vararg details: String?) {
      errorCount.incrementAndGet()
    }
  }
}
