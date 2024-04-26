// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SequentialProgressReporterTest {

  @Test
  fun `negative size`() {
    assertLogThrows<IllegalArgumentException> {
      progressReporterTest {
        reportSequentialProgress(-1) {
          fail()
        }
      }
    }
  }

  @Test
  fun empty() {
    progressReporterTest {
      reportSequentialProgress {}
    }
  }

  @ValueSource(ints = [-40, 0, 101])
  @ParameterizedTest
  fun `end fraction must be greater than 0 and less or equal to size`(endFraction: Int) {
    progressReporterTest {
      reportSequentialProgress { reporter ->
        assertLogThrows<IllegalArgumentException> {
          reporter.nextStep(endFraction)
          fail()
        }
      }
    }
  }

  @ValueSource(ints = [-40, 146])
  @ParameterizedTest
  fun `work size must be greater or equal to 0 and less or equal to size`(workSize: Int) {
    progressReporterTest {
      reportSequentialProgress { reporter ->
        assertLogThrows<IllegalArgumentException> {
          reporter.sizedStep(workSize)
          fail()
        }
      }
    }
  }

  @Test
  fun `two subsequent steps must not request the same end fraction`() {
    progressReporterTest(
      ExpectedState(fraction = 0.0),
    ) {
      reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 30)
        assertLogThrows<IllegalArgumentException> {
          reporter.nextStep(endFraction = 30)
          fail()
        }
      }
    }
  }

  @ValueSource(ints = [0, 10, 100])
  @ParameterizedTest
  fun `total size cannot exceed size`(size: Int) {
    progressReporterTest {
      reportSequentialProgress(size) { reporter ->
        reporter.indeterminateStep() // ok
        assertLogThrows<IllegalArgumentException> {
          reporter.sizedStep(size + 1)
          fail()
        }
        reporter.indeterminateStep() // ok
      }
    }
  }

  @Test
  fun `steps no text`() {
    progressReporterTest(
      ExpectedState(fraction = 0.0),
      ExpectedState(fraction = 0.42),
    ) {
      reportSequentialProgress { reporter ->
        reporter.indeterminateStep()
        reporter.indeterminateStep()
        reporter.nextStep(endFraction = 42)
        reporter.nextStep(endFraction = 100)
        reporter.indeterminateStep()
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
      reportSequentialProgress { reporter ->
        reporter.indeterminateStep("initial 0")
        reporter.indeterminateStep("initial 1")
        reporter.nextStep(endFraction = 42, text = "step 0")
        reporter.indeterminateStep("indeterminate between")
        reporter.nextStep(endFraction = 100, text = "step 1")
        reporter.indeterminateStep("last indeterminate")
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
      reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 30, text = "s0")
        reporter.nextStep(endFraction = 80)
        reporter.nextStep(endFraction = 100, text = "s2")
      }
    }

    progressReporterTest(
      ExpectedState(fraction = 0.0, text = null),
      ExpectedState(fraction = 0.3, text = null),
      ExpectedState(fraction = 0.3, text = "s1"),
      ExpectedState(fraction = 0.8, text = null),
    ) {
      reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 30)
        reporter.nextStep(endFraction = 80, text = "s1")
        reporter.nextStep(endFraction = 100)
      }
    }
  }

  @Test
  fun `sequential steps top level`() {
    progressReporterTest(
      ExpectedState(fraction = 0.0, text = "s0"),
      ExpectedState(fraction = 0.3, text = null),
      ExpectedState(fraction = 0.3, text = "s1"),
      ExpectedState(fraction = 0.8, text = null),
      ExpectedState(fraction = 0.8, text = "s2"),
    ) {
      sequentialTest()
    }
  }

  @Test
  fun `sequential steps inside indeterminate step no text`() {
    progressReporterTest(
      ExpectedState(text = "s0"),
      ExpectedState(text = null),
      ExpectedState(text = "s1"),
      ExpectedState(text = null),
      ExpectedState(text = "s2"),
      ExpectedState(text = null),
    ) {
      reportSequentialProgress { reporter ->
        reporter.indeterminateStep {
          sequentialTest()
        }
      }
    }
  }

  @Test
  fun `sequential steps inside determinate step no text`() {
    progressReporterTest(
      ExpectedState(fraction = 0.0, text = null),
      ExpectedState(fraction = 0.0, text = "s0"),
      ExpectedState(fraction = 0.3, text = null),
      ExpectedState(fraction = 0.3, text = "s1"),
      ExpectedState(fraction = 0.8, text = null),
      ExpectedState(fraction = 0.8, text = "s2"),
    ) {
      reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 100) {
          sequentialTest()
        }
      }
    }
  }

  @Test
  fun `sequential steps inside determinate step no text scaled`() {
    progressReporterTest(
      ExpectedState(fraction = 0.0 * 70 / 99, text = null),
      ExpectedState(fraction = 0.0 * 70 / 99, text = "s0"),
      ExpectedState(fraction = 0.3 * 70 / 99, text = null),
      ExpectedState(fraction = 0.3 * 70 / 99, text = "s1"),
      ExpectedState(fraction = 0.8 * 70 / 99, text = null),
      ExpectedState(fraction = 0.8 * 70 / 99, text = "s2"),
      ExpectedState(fraction = 1.0 * 70 / 99, text = null),
    ) {
      reportSequentialProgress(99) { reporter ->
        reporter.nextStep(endFraction = 70) {
          sequentialTest()
        }
      }
    }
  }

  @Test
  fun `sequential steps inside indeterminate step with text`() {
    val expected = arrayOf(
      ExpectedState(text = "outer", details = null),
      ExpectedState(text = "outer", details = "s0"),
      ExpectedState(text = "outer", details = null),
      ExpectedState(text = "outer", details = "s1"),
      ExpectedState(text = "outer", details = null),
      ExpectedState(text = "outer", details = "s2"),
      ExpectedState(text = "outer", details = null),
    )
    progressReporterTest(*expected, ExpectedState(text = null, details = null)) {
      reportSequentialProgress { reporter ->
        reporter.indeterminateStep("outer") {
          sequentialTest()
        }
      }
    }
    progressReporterTest(*expected, ExpectedState(text = null, details = null)) {
      reportSequentialProgress { reporter ->
        reporter.indeterminateStep {
          withProgressText("outer") {
            sequentialTest()
          }
        }
      }
    }
    progressReporterTest(*expected, ExpectedState(fraction = 1.0, text = "outer", details = null)) {
      withProgressText("outer") {
        reportSequentialProgress { reporter ->
          reporter.indeterminateStep {
            sequentialTest()
          }
        }
      }
    }
  }

  @Test
  fun `sequential steps inside determinate step with text`() {
    val expected = arrayOf(
      ExpectedState(fraction = 0.0, text = "outer", details = null),
      ExpectedState(fraction = 0.0, text = "outer", details = "s0"),
      ExpectedState(fraction = 0.3, text = "outer", details = null),
      ExpectedState(fraction = 0.3, text = "outer", details = "s1"),
      ExpectedState(fraction = 0.8, text = "outer", details = null),
      ExpectedState(fraction = 0.8, text = "outer", details = "s2"),
      ExpectedState(fraction = 1.0, text = "outer", details = null),
    )
    progressReporterTest(*expected) {
      reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 100, text = "outer") {
          sequentialTest()
        }
      }
    }
    progressReporterTest(
      ExpectedState(fraction = 0.0, text = null, details = null),
      *expected,
    ) {
      reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 100) {
          withProgressText(text = "outer") {
            sequentialTest()
          }
        }
      }
    }
    progressReporterTest(
      ExpectedState(fraction = null, text = "outer", details = null),
      *expected,
    ) {
      withProgressText(text = "outer") {
        reportSequentialProgress { reporter ->
          reporter.nextStep(endFraction = 100) {
            sequentialTest()
          }
        }
      }
    }
  }

  @Test
  fun `sequential steps inside determinate step with text scaled`() {
    val expected = arrayOf(
      ExpectedState(fraction = 0.0 * 70 / 99, text = "outer", details = null),
      ExpectedState(fraction = 0.0 * 70 / 99, text = "outer", details = "s0"),
      ExpectedState(fraction = 0.3 * 70 / 99, text = "outer", details = null),
      ExpectedState(fraction = 0.3 * 70 / 99, text = "outer", details = "s1"),
      ExpectedState(fraction = 0.8 * 70 / 99, text = "outer", details = null),
      ExpectedState(fraction = 0.8 * 70 / 99, text = "outer", details = "s2"),
      ExpectedState(fraction = 1.0 * 70 / 99, text = "outer", details = null),
    )
    progressReporterTest(*expected, ExpectedState(fraction = 1.0 * 70 / 99, text = null, details = null)) {
      reportSequentialProgress(99) { reporter ->
        reporter.nextStep(endFraction = 70, text = "outer") {
          sequentialTest()
        }
      }
    }
    progressReporterTest(
      ExpectedState(fraction = 0.0 * 70 / 99, text = null, details = null),
      *expected,
      ExpectedState(fraction = 1.0 * 70 / 99, text = null, details = null),
    ) {
      reportSequentialProgress(99) { reporter ->
        reporter.nextStep(endFraction = 70) {
          withProgressText(text = "outer") {
            sequentialTest()
          }
        }
      }
    }
    progressReporterTest(
      ExpectedState(fraction = null, text = "outer", details = null),
      *expected,
      ExpectedState(fraction = 1.0, text = "outer", details = null),
    ) {
      withProgressText(text = "outer") {
        reportSequentialProgress(99) { reporter ->
          reporter.nextStep(endFraction = 70) {
            sequentialTest()
          }
        }
      }
    }
  }

  private suspend fun sequentialTest() {
    reportSequentialProgress { reporter ->
      reporter.nextStep(endFraction = 30, text = "s0")
      reporter.nextStep(endFraction = 80, text = "s1")
      reporter.nextStep(endFraction = 100, text = "s2")
    }
  }

  @Test
  fun `ultra nested`() {
    val i0 = "i0"
    val s0 = "s0"
    val i0t = "i0t"
    val s0t = "s0t"

    suspend fun steps(inner: suspend () -> Unit) {
      reportSequentialProgress { reporter ->
        reporter.indeterminateStep {
          inner()
        }
        reporter.nextStep(endFraction = 30) {
          inner()
        }
        reporter.indeterminateStep {
          withProgressText(i0t) {
            inner()
          }
        }
        reporter.nextStep(endFraction = 49) {
          withProgressText(s0t) {
            inner()
          }
        }
        reporter.indeterminateStep(text = i0) {
          inner()
        }
        reporter.nextStep(endFraction = 99, text = s0) {
          inner()
        }
      }
    }
    progressReporterTest(
      ExpectedState(fraction = null, text = i0t, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = s0t, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = i0, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = s0, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = i0t, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = s0t, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = i0, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = s0, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = i0t, details = null),
      ExpectedState(fraction = null, text = i0t, details = i0t),
      ExpectedState(fraction = null, text = i0t, details = null),
      ExpectedState(fraction = null, text = i0t, details = s0t),
      ExpectedState(fraction = null, text = i0t, details = null),
      ExpectedState(fraction = null, text = i0t, details = i0),
      ExpectedState(fraction = null, text = i0t, details = null),
      ExpectedState(fraction = null, text = i0t, details = s0),
      ExpectedState(fraction = null, text = i0t, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = s0t, details = null),
      ExpectedState(fraction = null, text = s0t, details = i0t),
      ExpectedState(fraction = null, text = s0t, details = null),
      ExpectedState(fraction = null, text = s0t, details = s0t),
      ExpectedState(fraction = null, text = s0t, details = null),
      ExpectedState(fraction = null, text = s0t, details = i0),
      ExpectedState(fraction = null, text = s0t, details = null),
      ExpectedState(fraction = null, text = s0t, details = s0),
      ExpectedState(fraction = null, text = s0t, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = i0, details = null),
      ExpectedState(fraction = null, text = i0, details = i0t),
      ExpectedState(fraction = null, text = i0, details = null),
      ExpectedState(fraction = null, text = i0, details = s0t),
      ExpectedState(fraction = null, text = i0, details = null),
      ExpectedState(fraction = null, text = i0, details = i0),
      ExpectedState(fraction = null, text = i0, details = null),
      ExpectedState(fraction = null, text = i0, details = s0),
      ExpectedState(fraction = null, text = i0, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = null, text = s0, details = null),
      ExpectedState(fraction = null, text = s0, details = i0t),
      ExpectedState(fraction = null, text = s0, details = null),
      ExpectedState(fraction = null, text = s0, details = s0t),
      ExpectedState(fraction = null, text = s0, details = null),
      ExpectedState(fraction = null, text = s0, details = i0),
      ExpectedState(fraction = null, text = s0, details = null),
      ExpectedState(fraction = null, text = s0, details = s0),
      ExpectedState(fraction = null, text = s0, details = null),
      ExpectedState(fraction = null, text = null, details = null),
      ExpectedState(fraction = 0.0, text = null, details = null),
      ExpectedState(fraction = 0.0, text = i0t, details = null),
      ExpectedState(fraction = 0.0, text = null, details = null),
      ExpectedState(fraction = 0.0, text = s0t, details = null),
      ExpectedState(fraction = 0.0, text = null, details = null),
      ExpectedState(fraction = 0.0, text = i0, details = null),
      ExpectedState(fraction = 0.0, text = null, details = null),
      ExpectedState(fraction = 0.0, text = s0, details = null),
      ExpectedState(fraction = 0.0, text = null, details = null),
      ExpectedState(fraction = 0.026999999999999996, text = null, details = null),
      ExpectedState(fraction = 0.026999999999999996, text = i0t, details = null),
      ExpectedState(fraction = 0.026999999999999996, text = null, details = null),
      ExpectedState(fraction = 0.026999999999999996, text = s0t, details = null),
      ExpectedState(fraction = 0.0441, text = null, details = null),
      ExpectedState(fraction = 0.0441, text = i0, details = null),
      ExpectedState(fraction = 0.0441, text = null, details = null),
      ExpectedState(fraction = 0.0441, text = s0, details = null),
      ExpectedState(fraction = 0.0891, text = null, details = null),
      ExpectedState(fraction = 0.09, text = null, details = null),
      ExpectedState(fraction = 0.09, text = i0t, details = null),
      ExpectedState(fraction = 0.09, text = i0t, details = i0t),
      ExpectedState(fraction = 0.09, text = i0t, details = null),
      ExpectedState(fraction = 0.09, text = i0t, details = s0t),
      ExpectedState(fraction = 0.09, text = i0t, details = null),
      ExpectedState(fraction = 0.09, text = i0t, details = i0),
      ExpectedState(fraction = 0.09, text = i0t, details = null),
      ExpectedState(fraction = 0.09, text = i0t, details = s0),
      ExpectedState(fraction = 0.09, text = i0t, details = null),
      ExpectedState(fraction = 0.09, text = null, details = null),
      ExpectedState(fraction = 0.09, text = s0t, details = null),
      ExpectedState(fraction = 0.10710000000000001, text = s0t, details = null),
      ExpectedState(fraction = 0.10710000000000001, text = s0t, details = i0t),
      ExpectedState(fraction = 0.10710000000000001, text = s0t, details = null),
      ExpectedState(fraction = 0.10710000000000001, text = s0t, details = s0t),
      ExpectedState(fraction = 0.11793, text = s0t, details = null),
      ExpectedState(fraction = 0.11793, text = s0t, details = i0),
      ExpectedState(fraction = 0.11793, text = s0t, details = null),
      ExpectedState(fraction = 0.11793, text = s0t, details = s0),
      ExpectedState(fraction = 0.14643, text = s0t, details = null),
      ExpectedState(fraction = 0.147, text = s0t, details = null),
      ExpectedState(fraction = 0.147, text = null, details = null),
      ExpectedState(fraction = 0.147, text = i0, details = null),
      ExpectedState(fraction = 0.147, text = i0, details = i0t),
      ExpectedState(fraction = 0.147, text = i0, details = null),
      ExpectedState(fraction = 0.147, text = i0, details = s0t),
      ExpectedState(fraction = 0.147, text = i0, details = null),
      ExpectedState(fraction = 0.147, text = i0, details = i0),
      ExpectedState(fraction = 0.147, text = i0, details = null),
      ExpectedState(fraction = 0.147, text = i0, details = s0),
      ExpectedState(fraction = 0.147, text = i0, details = null),
      ExpectedState(fraction = 0.147, text = null, details = null),
      ExpectedState(fraction = 0.147, text = s0, details = null),
      ExpectedState(fraction = 0.192, text = s0, details = null),
      ExpectedState(fraction = 0.192, text = s0, details = i0t),
      ExpectedState(fraction = 0.192, text = s0, details = null),
      ExpectedState(fraction = 0.192, text = s0, details = s0t),
      ExpectedState(fraction = 0.22049999999999997, text = s0, details = null),
      ExpectedState(fraction = 0.22049999999999997, text = s0, details = i0),
      ExpectedState(fraction = 0.22049999999999997, text = s0, details = null),
      ExpectedState(fraction = 0.22049999999999997, text = s0, details = s0),
      ExpectedState(fraction = 0.2955, text = s0, details = null),
      ExpectedState(fraction = 0.29699999999999993, text = s0, details = null),
      ExpectedState(fraction = 0.29699999999999993, text = null, details = null),
      ExpectedState(fraction = 0.3, text = null, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = i0t),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = s0t),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = i0),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = s0),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = i0t),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = s0t),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = i0),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = s0),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = i0t),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = s0t),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = i0),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = i0t, details = s0),
      ExpectedState(fraction = 0.3, text = i0t, details = null),
      ExpectedState(fraction = 0.3, text = null, details = null),
      ExpectedState(fraction = 0.3, text = s0t, details = null),
      ExpectedState(fraction = 0.3, text = s0t, details = i0t),
      ExpectedState(fraction = 0.3, text = s0t, details = null),
      ExpectedState(fraction = 0.3, text = s0t, details = s0t),
      ExpectedState(fraction = 0.3, text = s0t, details = null),
      ExpectedState(fraction = 0.3, text = s0t, details = i0),
      ExpectedState(fraction = 0.3, text = s0t, details = null),
      ExpectedState(fraction = 0.3, text = s0t, details = s0),
      ExpectedState(fraction = 0.3, text = s0t, details = null),
      ExpectedState(fraction = 0.3171, text = s0t, details = null),
      ExpectedState(fraction = 0.3171, text = s0t, details = i0t),
      ExpectedState(fraction = 0.3171, text = s0t, details = null),
      ExpectedState(fraction = 0.3171, text = s0t, details = s0t),
      ExpectedState(fraction = 0.32793, text = s0t, details = null),
      ExpectedState(fraction = 0.32793, text = s0t, details = i0),
      ExpectedState(fraction = 0.32793, text = s0t, details = null),
      ExpectedState(fraction = 0.32793, text = s0t, details = s0),
      ExpectedState(fraction = 0.35643, text = s0t, details = null),
      ExpectedState(fraction = 0.35700000000000004, text = s0t, details = null),
      ExpectedState(fraction = 0.35700000000000004, text = s0t, details = i0t),
      ExpectedState(fraction = 0.35700000000000004, text = s0t, details = null),
      ExpectedState(fraction = 0.35700000000000004, text = s0t, details = s0t),
      ExpectedState(fraction = 0.36783, text = s0t, details = s0t),
      ExpectedState(fraction = 0.374689, text = s0t, details = s0t),
      ExpectedState(fraction = 0.39273899999999995, text = s0t, details = s0t),
      ExpectedState(fraction = 0.39309999999999995, text = s0t, details = s0t),
      ExpectedState(fraction = 0.39309999999999995, text = s0t, details = null),
      ExpectedState(fraction = 0.39309999999999995, text = s0t, details = i0),
      ExpectedState(fraction = 0.39309999999999995, text = s0t, details = null),
      ExpectedState(fraction = 0.39309999999999995, text = s0t, details = s0),
      ExpectedState(fraction = 0.4216, text = s0t, details = s0),
      ExpectedState(fraction = 0.43965, text = s0t, details = s0),
      ExpectedState(fraction = 0.48714999999999997, text = s0t, details = s0),
      ExpectedState(fraction = 0.4881, text = s0t, details = s0),
      ExpectedState(fraction = 0.4881, text = s0t, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = s0t, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = null, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = i0t),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = s0t),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = i0),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = s0),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = i0t),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = s0t),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = i0),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = s0),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = i0t),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = s0t),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = i0),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = s0),
      ExpectedState(fraction = 0.48999999999999994, text = i0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = null, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = s0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = s0, details = i0t),
      ExpectedState(fraction = 0.48999999999999994, text = s0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = s0, details = s0t),
      ExpectedState(fraction = 0.48999999999999994, text = s0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = s0, details = i0),
      ExpectedState(fraction = 0.48999999999999994, text = s0, details = null),
      ExpectedState(fraction = 0.48999999999999994, text = s0, details = s0),
      ExpectedState(fraction = 0.48999999999999994, text = s0, details = null),
      ExpectedState(fraction = 0.5349999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.5349999999999999, text = s0, details = i0t),
      ExpectedState(fraction = 0.5349999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.5349999999999999, text = s0, details = s0t),
      ExpectedState(fraction = 0.5634999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.5634999999999999, text = s0, details = i0),
      ExpectedState(fraction = 0.5634999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.5634999999999999, text = s0, details = s0),
      ExpectedState(fraction = 0.6385, text = s0, details = null),
      ExpectedState(fraction = 0.6399999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.6399999999999999, text = s0, details = i0t),
      ExpectedState(fraction = 0.6399999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.6399999999999999, text = s0, details = s0t),
      ExpectedState(fraction = 0.6685, text = s0, details = s0t),
      ExpectedState(fraction = 0.6865499999999999, text = s0, details = s0t),
      ExpectedState(fraction = 0.7340499999999999, text = s0, details = s0t),
      ExpectedState(fraction = 0.7349999999999999, text = s0, details = s0t),
      ExpectedState(fraction = 0.7349999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.7349999999999999, text = s0, details = i0),
      ExpectedState(fraction = 0.7349999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.7349999999999999, text = s0, details = s0),
      ExpectedState(fraction = 0.8099999999999998, text = s0, details = s0),
      ExpectedState(fraction = 0.8574999999999998, text = s0, details = s0),
      ExpectedState(fraction = 0.9824999999999998, text = s0, details = s0),
      ExpectedState(fraction = 0.9849999999999999, text = s0, details = s0),
      ExpectedState(fraction = 0.9849999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.9899999999999999, text = s0, details = null),
      ExpectedState(fraction = 0.9899999999999999, text = null, details = null),
    ) {
      steps {
        steps {
          steps {}
        }
      }
    }
  }
}
