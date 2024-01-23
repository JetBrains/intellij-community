// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConcurrentProgressReporterTest {

  @Test
  fun `negative size`() {
    assertLogThrows<IllegalArgumentException> {
      progressReporterTest {
        reportProgress(-1) {
          fail()
        }
      }
    }
  }

  @Test
  fun empty() {
    progressReporterTest {
      reportProgress {}
    }
  }

  @ValueSource(ints = [-40, 146])
  @ParameterizedTest
  fun `work size must be greater or equal to 0 and less or equal to size`(workSize: Int) {
    progressReporterTest {
      reportProgress { reporter ->
        assertLogThrows<IllegalArgumentException> {
          reporter.sizedStep(workSize) {
            fail()
          }
        }
      }
    }
  }

  @ValueSource(ints = [0, 10, 100])
  @ParameterizedTest
  fun `total size cannot exceed size`(size: Int) {
    progressReporterTest {
      reportProgress(size) { reporter ->
        reporter.indeterminateStep {} // ok
        assertLogThrows<IllegalArgumentException> {
          reporter.sizedStep(size + 1) {
            fail()
          }
        }
        reporter.indeterminateStep {} // ok
      }
    }
  }

  @Test
  fun `steps no text`() {
    progressReporterTest(
      ExpectedState(fraction = 0.0),
      ExpectedState(fraction = 0.42),
    ) {
      reportProgress { reporter ->
        reporter.indeterminateStep {}
        reporter.indeterminateStep {}
        reporter.sizedStep(workSize = 42) {}
        reporter.sizedStep(workSize = 58) {}
        reporter.indeterminateStep {}
      }
    }
  }

  @Test
  fun `steps with text`() {
    progressReporterTest(
      ExpectedState(fraction = null, text = "initial 0"),
      ExpectedState(fraction = null, text = null),
      ExpectedState(fraction = null, text = "initial 1"),
      ExpectedState(fraction = null, text = null),
      ExpectedState(fraction = 0.0, text = "step 0"),
      ExpectedState(fraction = 0.42, text = null),
      ExpectedState(fraction = 0.42, text = "indeterminate between"),
      ExpectedState(fraction = 0.42, text = null),
      ExpectedState(fraction = 0.42, text = "step 1"),
      ExpectedState(fraction = 1.0, text = null),
      ExpectedState(fraction = 1.0, text = "last indeterminate"),
    ) {
      reportProgress { reporter ->
        reporter.indeterminateStep("initial 0") {}
        reporter.indeterminateStep("initial 1") {}
        reporter.sizedStep(workSize = 42, text = "step 0") {}
        reporter.indeterminateStep("indeterminate between") {}
        reporter.sizedStep(workSize = 58, text = "step 1") {}
        reporter.indeterminateStep("last indeterminate") {}
      }
    }
  }

  @Test
  fun `steps with and without text`() {
    progressReporterTest(
      ExpectedState(fraction = 0.0, text = "s0"),
      ExpectedState(fraction = 0.3, text = null),
      ExpectedState(fraction = 0.8, text = null),
      ExpectedState(fraction = 0.8, text = "s2"),
    ) {
      reportProgress { reporter ->
        reporter.sizedStep(workSize = 30, text = "s0") {}
        reporter.sizedStep(workSize = 50) {}
        reporter.sizedStep(workSize = 20, text = "s2") {}
      }
    }
    progressReporterTest(
      ExpectedState(fraction = 0.0, text = null),
      ExpectedState(fraction = 0.3, text = null),
      ExpectedState(fraction = 0.3, text = "s1"),
      ExpectedState(fraction = 0.8, text = null),
    ) {
      reportProgress { reporter ->
        reporter.sizedStep(workSize = 30) {}
        reporter.sizedStep(workSize = 50, text = "s1") {}
        reporter.sizedStep(workSize = 20) {}
      }
    }
  }

  @Test
  fun `concurrent steps no text`() {
    progressReporterTest(
      ExpectedState(text = null, fraction = 0.0),
      ExpectedState(text = "s0s0", fraction = (30 * 0.0) / 100),            // s0s0 started
      ExpectedState(text = "s1s0", fraction = (30 * 0.0 + 70 * 0.0) / 100), // s1s0 started
      ExpectedState(text = "s1s0", fraction = (30 * 0.4 + 70 * 0.0) / 100), // s0s0 finished
      ExpectedState(text = "s0s1", fraction = (30 * 0.4 + 70 * 0.0) / 100), // s0s1 started
      ExpectedState(text = "s0s1", fraction = (30 * 0.4 + 70 * 0.5) / 100), // s1s0 finished
      ExpectedState(text = "s1s1", fraction = (30 * 0.4 + 70 * 0.5) / 100), // s1s1 started
      ExpectedState(text = "s1s1", fraction = (30 * 1.0 + 70 * 0.5) / 100), // s0s1 finished
      // s1s1 finished
    ) {
      reportProgressScope { reporter ->
        val step1 = launch {
          reporter.sizedStep(workSize = 30) {
            reportSequentialProgress { innerReporter ->
              innerReporter.nextStep(endFraction = 40, text = "s0s0")
              yield()
              innerReporter.nextStep(endFraction = 100, text = "s0s1")
              awaitCancellation()
            }
          }
        }
        launch {
          reporter.sizedStep(workSize = 70) {
            reportSequentialProgress { innerReporter ->
              innerReporter.nextStep(endFraction = 50, text = "s1s0")
              yield()
              innerReporter.nextStep(endFraction = 100, text = "s1s1")
              step1.cancelAndJoin()
            }
          }
        }
      }
    }
  }

  @Test
  fun `concurrent steps with text`() {
    progressReporterTest(
      ExpectedState(fraction = 0.3 * 0.0, text = "s0", details = null),                     // s0 started
      ExpectedState(fraction = 0.3 * 0.0, text = "s0", details = "s0s0"),                   // s0s0 started
      ExpectedState(fraction = (30 * 0.0 + 70 * 0.0) / 100, text = "s1", details = null),   // s1 started
      ExpectedState(fraction = (30 * 0.0 + 70 * 0.0) / 100, text = "s1", details = "s1s0"), // s1s0 started
      ExpectedState(fraction = (30 * 0.4 + 70 * 0.0) / 100, text = "s0", details = null),   // s0s0 finished
      ExpectedState(fraction = (30 * 0.4 + 70 * 0.0) / 100, text = "s0", details = "s0s1"), // s0s1 started
      ExpectedState(fraction = (30 * 0.4 + 70 * 0.5) / 100, text = "s1", details = null),   // s1s0 finished
      ExpectedState(fraction = (30 * 0.4 + 70 * 0.5) / 100, text = "s1", details = "s1s1"), // s1s1 started
      ExpectedState(fraction = (30 * 1.0 + 70 * 0.5) / 100, text = "s0", details = null),   // s0s1 finished
      ExpectedState(fraction = (30 * 1.0 + 70 * 0.5) / 100, text = "s1", details = "s1s1"), // s0 finished
      ExpectedState(fraction = 1.0, text = "s1", details = null),                           // s1s1 finished
      // s1 finished
    ) {
      reportProgressScope { reporter ->
        val step1 = launch {
          reporter.sizedStep(workSize = 30, text = "s0") {
            reportSequentialProgress { innerReporter ->
              innerReporter.nextStep(endFraction = 40, text = "s0s0")
              yield()
              innerReporter.nextStep(endFraction = 100, text = "s0s1")
              awaitCancellation()
            }
          }
        }
        launch {
          reporter.sizedStep(workSize = 70, text = "s1") {
            reportSequentialProgress { innerReporter ->
              innerReporter.nextStep(endFraction = 50, text = "s1s0")
              yield()
              innerReporter.nextStep(endFraction = 100, text = "s1s1")
              step1.cancelAndJoin()
            }
          }
        }
      }
    }
  }

  @Test
  fun `ultra nested`() {
    val i0 = "i0"
    val s0 = "s0"
    val i0t = "i0t"
    val s0t = "s0t"

    suspend fun concurrentSteps(inner: suspend CoroutineScope.() -> Unit) {
      reportProgressScope { reporter ->
        launch {
          reporter.indeterminateStep {
            inner()
          }
        }
        launch {
          reporter.sizedStep(workSize = 30) {
            inner()
          }
        }
        launch {
          reporter.indeterminateStep {
            withProgressText(i0t) {
              inner()
            }
          }
        }
        launch {
          reporter.sizedStep(workSize = 19) {
            withProgressText(s0t) {
              inner()
            }
          }
        }
        launch {
          reporter.indeterminateStep(text = i0) {
            inner()
          }
        }
        launch {
          reporter.sizedStep(workSize = 40, text = s0) {
            inner()
          }
        }
      }
    }

    progressReporterTest(
      ExpectedState(fraction = 0.0, text = null, details = null),
      ExpectedState(fraction = 0.0, text = i0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = i0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = i0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = i0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = i0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = i0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = i0t, details = i0t),
      ExpectedState(fraction = 0.0, text = i0t, details = s0t),
      ExpectedState(fraction = 0.0, text = i0t, details = i0),
      ExpectedState(fraction = 0.0, text = i0t, details = s0),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = i0t),
      ExpectedState(fraction = 0.0, text = s0t, details = s0t),
      ExpectedState(fraction = 0.0, text = s0t, details = i0),
      ExpectedState(fraction = 0.0, text = s0t, details = s0),
      ExpectedState(fraction = 0.0, text = i0, details = i0t),
      ExpectedState(fraction = 0.0, text = i0, details = s0t),
      ExpectedState(fraction = 0.0, text = i0, details = i0),
      ExpectedState(fraction = 0.0, text = i0, details = s0),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = i0t),
      ExpectedState(fraction = 0.0, text = s0, details = s0t),
      ExpectedState(fraction = 0.0, text = s0, details = i0),
      ExpectedState(fraction = 0.0, text = s0, details = s0),
      ExpectedState(fraction = 0.0, text = i0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = i0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = i0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = i0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = i0t, details = i0t),
      ExpectedState(fraction = 0.0, text = i0t, details = s0t),
      ExpectedState(fraction = 0.0, text = i0t, details = i0),
      ExpectedState(fraction = 0.0, text = i0t, details = s0),
      ExpectedState(fraction = 0.0, text = s0t, details = i0t),
      ExpectedState(fraction = 0.0, text = s0t, details = s0t),
      ExpectedState(fraction = 0.0, text = s0t, details = i0),
      ExpectedState(fraction = 0.0, text = s0t, details = s0),
      ExpectedState(fraction = 0.0, text = i0, details = i0t),
      ExpectedState(fraction = 0.0, text = i0, details = s0t),
      ExpectedState(fraction = 0.0, text = i0, details = i0),
      ExpectedState(fraction = 0.0, text = i0, details = s0),
      ExpectedState(fraction = 0.0, text = s0, details = i0t),
      ExpectedState(fraction = 0.0, text = s0, details = s0t),
      ExpectedState(fraction = 0.0, text = s0, details = i0),
      ExpectedState(fraction = 0.0, text = s0, details = s0),
      ExpectedState(fraction = 0.0, text = i0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = i0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = i0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = i0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = i0t, details = i0t),
      ExpectedState(fraction = 0.0, text = i0t, details = s0t),
      ExpectedState(fraction = 0.0, text = i0t, details = i0),
      ExpectedState(fraction = 0.0, text = i0t, details = s0),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = i0t),
      ExpectedState(fraction = 0.0, text = s0t, details = s0t),
      ExpectedState(fraction = 0.0, text = s0t, details = i0),
      ExpectedState(fraction = 0.0, text = s0t, details = s0),
      ExpectedState(fraction = 0.0, text = i0, details = i0t),
      ExpectedState(fraction = 0.0, text = i0, details = s0t),
      ExpectedState(fraction = 0.0, text = i0, details = i0),
      ExpectedState(fraction = 0.0, text = i0, details = s0),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = i0t),
      ExpectedState(fraction = 0.0, text = s0, details = s0t),
      ExpectedState(fraction = 0.0, text = s0, details = i0),
      ExpectedState(fraction = 0.0, text = s0, details = s0),
      ExpectedState(fraction = 0.0, text = i0t, details = i0t),
      ExpectedState(fraction = 0.0, text = i0t, details = s0t),
      ExpectedState(fraction = 0.0, text = i0t, details = i0),
      ExpectedState(fraction = 0.0, text = i0t, details = s0),
      ExpectedState(fraction = 0.0, text = i0t, details = i0t),
      ExpectedState(fraction = 0.0, text = i0t, details = s0t),
      ExpectedState(fraction = 0.0, text = i0t, details = i0),
      ExpectedState(fraction = 0.0, text = i0t, details = s0),
      ExpectedState(fraction = 0.0, text = s0t, details = i0t),
      ExpectedState(fraction = 0.0, text = s0t, details = s0t),
      ExpectedState(fraction = 0.0, text = s0t, details = i0),
      ExpectedState(fraction = 0.0, text = s0t, details = s0),
      ExpectedState(fraction = 0.0, text = s0t, details = i0t),
      ExpectedState(fraction = 0.0, text = s0t, details = s0t),
      ExpectedState(fraction = 0.0, text = s0t, details = i0),
      ExpectedState(fraction = 0.0, text = s0t, details = s0),
      ExpectedState(fraction = 0.0, text = s0t, details = s0t),
      ExpectedState(fraction = 0.0, text = s0t, details = s0),
      ExpectedState(fraction = 0.0, text = i0, details = i0t),
      ExpectedState(fraction = 0.0, text = i0, details = s0t),
      ExpectedState(fraction = 0.0, text = i0, details = i0),
      ExpectedState(fraction = 0.0, text = i0, details = s0),
      ExpectedState(fraction = 0.0, text = i0, details = i0t),
      ExpectedState(fraction = 0.0, text = i0, details = s0t),
      ExpectedState(fraction = 0.0, text = i0, details = i0),
      ExpectedState(fraction = 0.0, text = i0, details = s0),
      ExpectedState(fraction = 0.0, text = s0, details = i0t),
      ExpectedState(fraction = 0.0, text = s0, details = s0t),
      ExpectedState(fraction = 0.0, text = s0, details = i0),
      ExpectedState(fraction = 0.0, text = s0, details = s0),
      ExpectedState(fraction = 0.0, text = s0, details = i0t),
      ExpectedState(fraction = 0.0, text = s0, details = s0t),
      ExpectedState(fraction = 0.0, text = s0, details = i0),
      ExpectedState(fraction = 0.0, text = s0, details = s0),
      ExpectedState(fraction = 0.0, text = s0, details = s0t),
      ExpectedState(fraction = 0.0, text = s0, details = s0),
      ExpectedState(fraction = 0.0, text = i0t, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = i0, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.026999999999999996, text = s0, details = null),
      ExpectedState(fraction = 0.0441, text = s0, details = null),
      ExpectedState(fraction = 0.08010000000000002, text = s0, details = s0),
      ExpectedState(fraction = 0.08010000000000002, text = i0t, details = null),
      ExpectedState(fraction = 0.09720000000000002, text = s0t, details = s0),
      ExpectedState(fraction = 0.10803000000000001, text = s0t, details = s0),
      ExpectedState(fraction = 0.13083000000000003, text = s0t, details = null),
      ExpectedState(fraction = 0.13083000000000003, text = i0, details = null),
      ExpectedState(fraction = 0.16683000000000003, text = s0, details = s0),
      ExpectedState(fraction = 0.18963000000000002, text = s0, details = s0),
      ExpectedState(fraction = 0.23763, text = s0, details = null),
      ExpectedState(fraction = 0.25473, text = s0t, details = s0),
      ExpectedState(fraction = 0.26556, text = s0t, details = s0),
      ExpectedState(fraction = 0.28836, text = s0t, details = s0),
      ExpectedState(fraction = 0.29919, text = s0t, details = s0t),
      ExpectedState(fraction = 0.306049, text = s0t, details = s0t),
      ExpectedState(fraction = 0.320489, text = s0t, details = s0t),
      ExpectedState(fraction = 0.34328900000000007, text = s0t, details = s0),
      ExpectedState(fraction = 0.3577290000000001, text = s0t, details = s0),
      ExpectedState(fraction = 0.38812900000000006, text = s0t, details = s0),
      ExpectedState(fraction = 0.4241290000000001, text = s0, details = s0),
      ExpectedState(fraction = 0.4469290000000001, text = s0, details = s0),
      ExpectedState(fraction = 0.49492900000000006, text = s0, details = s0),
      ExpectedState(fraction = 0.5177290000000001, text = s0, details = s0t),
      ExpectedState(fraction = 0.532169, text = s0, details = s0t),
      ExpectedState(fraction = 0.562569, text = s0, details = s0t),
      ExpectedState(fraction = 0.610569, text = s0, details = s0),
      ExpectedState(fraction = 0.640969, text = s0, details = s0),
      ExpectedState(fraction = 0.7049690000000001, text = s0, details = s0),
      ExpectedState(fraction = 0.7049690000000001, text = i0t, details = null),
      ExpectedState(fraction = 0.7049690000000001, text = s0, details = null),
      ExpectedState(fraction = 0.7049690000000001, text = s0t, details = null),
      ExpectedState(fraction = 0.7049690000000001, text = s0, details = null),
      ExpectedState(fraction = 0.7049690000000001, text = i0, details = null),
      ExpectedState(fraction = 0.7049690000000001, text = s0, details = null),
      ExpectedState(fraction = 0.7049690000000001, text = s0, details = s0),
      ExpectedState(fraction = 0.7148690000000001, text = s0, details = null),
      ExpectedState(fraction = 0.7148690000000001, text = i0t, details = null),
      ExpectedState(fraction = 0.7148690000000001, text = s0, details = null),
      ExpectedState(fraction = 0.721139, text = s0t, details = null),
      ExpectedState(fraction = 0.721139, text = s0, details = null),
      ExpectedState(fraction = 0.721139, text = i0, details = null),
      ExpectedState(fraction = 0.721139, text = s0, details = null),
      ExpectedState(fraction = 0.7343390000000001, text = s0, details = null),
      ExpectedState(fraction = 0.7343390000000001, text = s0, details = s0),
      ExpectedState(fraction = 0.7343390000000001, text = i0t, details = null),
      ExpectedState(fraction = 0.7406090000000001, text = s0t, details = s0),
      ExpectedState(fraction = 0.74458, text = s0t, details = s0t),
      ExpectedState(fraction = 0.74458, text = s0t, details = s0),
      ExpectedState(fraction = 0.7529399999999999, text = s0t, details = s0),
      ExpectedState(fraction = 0.7529399999999999, text = s0t, details = null),
      ExpectedState(fraction = 0.7529399999999999, text = i0, details = null),
      ExpectedState(fraction = 0.76614, text = s0, details = s0),
      ExpectedState(fraction = 0.7745000000000001, text = s0, details = s0t),
      ExpectedState(fraction = 0.7745000000000001, text = s0, details = s0),
      ExpectedState(fraction = 0.7921, text = s0, details = s0),
      ExpectedState(fraction = 0.7921, text = s0, details = null),
      ExpectedState(fraction = 0.8251000000000001, text = s0, details = null),
      ExpectedState(fraction = 0.8251000000000001, text = i0t, details = null),
      ExpectedState(fraction = 0.8251000000000001, text = s0, details = null),
      ExpectedState(fraction = 0.8460000000000001, text = s0t, details = null),
      ExpectedState(fraction = 0.8460000000000001, text = s0, details = null),
      ExpectedState(fraction = 0.8460000000000001, text = i0, details = null),
      ExpectedState(fraction = 0.8460000000000001, text = s0, details = null),
      ExpectedState(fraction = 0.89, text = s0, details = null),
      ExpectedState(fraction = 0.89, text = null, details = null),
    ) {
      concurrentSteps {
        concurrentSteps {
          concurrentSteps { yield() }
        }
      }
    }
  }
}
