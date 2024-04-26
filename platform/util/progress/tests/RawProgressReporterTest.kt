// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class RawProgressReporterTest {

  @Test
  fun empty() {
    progressReporterTest {
      reportRawProgress {}
    }
  }

  @Test
  fun `incorrect fraction`() {
    progressReporterTest {
      reportRawProgress { reporter ->
        assertLogThrows<IllegalArgumentException> {
          reporter.fraction(-1.0 / Int.MAX_VALUE)
          fail()
        }
        assertLogThrows<IllegalArgumentException> {
          reporter.fraction(1.0 + 1.0 / Int.MAX_VALUE)
          fail()
        }
      }
    }
  }

  @Test
  fun `raw step`() {
    progressReporterTest(
      ExpectedState(fraction = 1.0, text = null, details = null),
      ExpectedState(fraction = 1.0, text = null, details = "ud"),
      ExpectedState(fraction = 1.0, text = "ut", details = "ud"),
      ExpectedState(fraction = 0.5, text = "ut", details = "ud"),
      ExpectedState(fraction = 0.5, text = null, details = "ud"),
      ExpectedState(fraction = null, text = null, details = "ud"),
    ) {
      rawTest()
    }
  }

  @Test
  fun `raw step inside indeterminate step no text`() {
    progressReporterTest(
      ExpectedState(text = null, details = "ud"),
      ExpectedState(text = "ut", details = "ud"),
      ExpectedState(text = null, details = "ud"),
      ExpectedState(text = null, details = null),
    ) {
      reportSequentialProgress { reporter ->
        reporter.indeterminateStep {
          rawTest()
        }
      }
    }
  }

  @Test
  fun `raw step inside determinate step no text`() {
    progressReporterTest(
      ExpectedState(fraction = 0.0, text = null, details = null),
      ExpectedState(fraction = 1.0, text = null, details = null),
      ExpectedState(fraction = 1.0, text = null, details = "ud"),
      ExpectedState(fraction = 1.0, text = "ut", details = "ud"),
      ExpectedState(fraction = 0.5, text = "ut", details = "ud"),
      ExpectedState(fraction = 0.5, text = null, details = "ud"),
      ExpectedState(fraction = 0.0, text = null, details = "ud"),
    ) {
      reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 100) {
          rawTest()
        }
      }
    }
  }

  @Test
  fun `raw step inside determinate step no text scaled`() {
    progressReporterTest(
      ExpectedState(fraction = 0.0 * 70 / 99, text = null, details = null),
      ExpectedState(fraction = 1.0 * 70 / 99, text = null, details = null),
      ExpectedState(fraction = 1.0 * 70 / 99, text = null, details = "ud"),
      ExpectedState(fraction = 1.0 * 70 / 99, text = "ut", details = "ud"),
      ExpectedState(fraction = 0.5 * 70 / 99, text = "ut", details = "ud"),
      ExpectedState(fraction = 0.5 * 70 / 99, text = null, details = "ud"),
      ExpectedState(fraction = 0.0 * 70 / 99, text = null, details = "ud"),
      ExpectedState(fraction = 1.0 * 70 / 99, text = null, details = null),
    ) {
      reportSequentialProgress(99) { reporter ->
        reporter.nextStep(endFraction = 70) {
          rawTest()
        }
      }
    }
  }

  @Test
  fun `raw step inside indeterminate step with text`() {
    val expected = arrayOf(
      ExpectedState(text = "outer", details = null),
      ExpectedState(text = "outer", details = "ut"),
      ExpectedState(text = "outer", details = null),
    )
    progressReporterTest(*expected, ExpectedState(text = null, details = null)) {
      reportSequentialProgress { reporter ->
        reporter.indeterminateStep(text = "outer") {
          rawTest()
        }
      }
    }
    progressReporterTest(*expected, ExpectedState(text = null, details = null)) {
      reportSequentialProgress { reporter ->
        reporter.indeterminateStep {
          withProgressText("outer") {
            rawTest()
          }
        }
      }
    }
    progressReporterTest(*expected, ExpectedState(fraction = 1.0, text = "outer", details = null)) {
      withProgressText("outer") {
        reportSequentialProgress { reporter ->
          reporter.indeterminateStep {
            rawTest()
          }
        }
      }
    }
  }

  @Test
  fun `raw step inside determinate step with text`() {
    val expected = arrayOf(
      ExpectedState(fraction = 0.0, text = "outer", details = null),
      ExpectedState(fraction = 1.0, text = "outer", details = null),
      ExpectedState(fraction = 1.0, text = "outer", details = "ut"),
      ExpectedState(fraction = 0.5, text = "outer", details = "ut"),
      ExpectedState(fraction = 0.5, text = "outer", details = null),
      ExpectedState(fraction = 0.0, text = "outer", details = null),
      ExpectedState(fraction = 1.0, text = "outer", details = null),
    )
    progressReporterTest(*expected) {
      reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 100, text = "outer") {
          rawTest()
        }
      }
    }
    progressReporterTest(ExpectedState(fraction = 0.0), *expected) {
      reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 100) {
          withProgressText(text = "outer") {
            rawTest()
          }
        }
      }
    }
    progressReporterTest(ExpectedState(text = "outer"), *expected) {
      withProgressText(text = "outer") {
        reportSequentialProgress { reporter ->
          reporter.nextStep(endFraction = 100) {
            rawTest()
          }
        }
      }
    }
  }

  @Test
  fun `raw step inside determinate step with text scaled`() {
    val expected = arrayOf(
      ExpectedState(fraction = 0.0 * 70 / 99, text = "outer", details = null),
      ExpectedState(fraction = 1.0 * 70 / 99, text = "outer", details = null),
      ExpectedState(fraction = 1.0 * 70 / 99, text = "outer", details = "ut"),
      ExpectedState(fraction = 0.5 * 70 / 99, text = "outer", details = "ut"),
      ExpectedState(fraction = 0.5 * 70 / 99, text = "outer", details = null),
      ExpectedState(fraction = 0.0 * 70 / 99, text = "outer", details = null),
      ExpectedState(fraction = 1.0 * 70 / 99, text = "outer", details = null),
    )
    progressReporterTest(*expected, ExpectedState(fraction = 1.0 * 70 / 99)) {
      reportSequentialProgress(size = 99) { reporter ->
        reporter.nextStep(endFraction = 70, text = "outer") {
          rawTest()
        }
      }
    }
    progressReporterTest(
      ExpectedState(fraction = 0.0),
      *expected,
      ExpectedState(fraction = 1.0 * 70 / 99),
    ) {
      reportSequentialProgress(size = 99) { reporter ->
        reporter.nextStep(endFraction = 70) {
          withProgressText(text = "outer") {
            rawTest()
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
        reportSequentialProgress(size = 99) { reporter ->
          reporter.nextStep(endFraction = 70) {
            rawTest()
          }
        }
      }
    }
  }

  private suspend fun rawTest() {
    reportRawProgress { reporter ->
      reporter.fraction(1.0)
      reporter.details("ud") // can set details without text
      reporter.text("ut")
      reporter.fraction(0.5) // can go back
      reporter.text(null) // clearing the text does not clear details
      reporter.fraction(null) // can become indeterminate after being determinate
    }
  }
}
