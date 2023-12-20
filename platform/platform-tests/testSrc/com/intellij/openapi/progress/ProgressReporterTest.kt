// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.platform.util.progress.asContextElement
import com.intellij.platform.util.progress.durationStep
import com.intellij.platform.util.progress.impl.ACCEPTABLE_FRACTION_OVERFLOW
import com.intellij.platform.util.progress.impl.ProgressState
import com.intellij.platform.util.progress.impl.TextDetailsProgressReporter
import com.intellij.platform.util.progress.indeterminateStep
import com.intellij.platform.util.progress.itemDuration
import com.intellij.platform.util.progress.progressReporter
import com.intellij.platform.util.progress.progressStep
import com.intellij.platform.util.progress.rawProgressReporter
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.init
import com.intellij.util.containers.tail
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProgressReporterTest {

  @Test
  fun empty() {
    progressReporterTest {}
  }

  @Test
  fun `end fraction must be greater than 0 and less or equal to 1`() {
    assertLogThrows<IllegalArgumentException> {
      progressReporterTest {
        progressStep(endFraction = -0.4) { fail() }
      }
    }
    assertLogThrows<IllegalArgumentException> {
      progressReporterTest {
        progressStep(endFraction = -0.0) { fail() }
      }
    }
    assertLogThrows<IllegalArgumentException> {
      progressReporterTest {
        progressStep(endFraction = 0.0) { fail() }
      }
    }
    assertLogThrows<IllegalArgumentException> {
      progressReporterTest {
        progressStep(endFraction = 1.4) { fail() }
      }
    }
  }

  @Test
  fun `two subsequent steps must not request the same end fraction`() {
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        progressStep(endFraction = 0.3) {}
        progressStep(endFraction = 0.3) { fail() }
      }
    }
  }

  @Test
  fun `duration must be greater or equal to 0 and less or equal to 1`() {
    assertLogThrows<IllegalArgumentException> {
      progressReporterTest {
        durationStep(duration = -0.4) { fail() }
      }
    }
    progressReporterTest {
      durationStep(duration = -0.0) {}
    }
    progressReporterTest {
      durationStep(duration = 0.0) {}
    }
    assertLogThrows<IllegalArgumentException> {
      progressReporterTest {
        durationStep(duration = 1.4) { fail() }
      }
    }
  }

  @Test
  fun `total duration cannot exceed 1`() {
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        durationStep(duration = 0.5) {}
        durationStep(duration = 0.51) { fail() }
      }
    }
  }

  @Test
  fun `indeterminate step no text`() {
    progressReporterTest {
      indeterminateStep {}
    }
  }

  @Test
  fun `determinate step no text no fraction`() {
    progressReporterTest(
      ProgressState(text = null, fraction = 0.0),
    ) {
      progressStep(endFraction = 1.0) {}
    }
  }

  @Test
  fun `determinate step no text with fraction`() {
    progressReporterTest(
      ProgressState(text = null, fraction = 0.0),
      ProgressState(text = null, fraction = 0.3),
    ) {
      progressStep(endFraction = 0.3) {}
    }
  }

  @Test
  fun `indeterminate step with text`() {
    progressReporterTest(
      ProgressState(text = "i0", fraction = -1.0),
      ProgressState(text = null, fraction = -1.0),
    ) {
      indeterminateStep(text = "i0") {}
    }
  }

  @Test
  fun `determinate step with text no fraction`() {
    progressReporterTest(
      ProgressState(text = "p0", fraction = 0.0),
    ) {
      progressStep(endFraction = 1.0, text = "p0") {}
    }
  }

  @Test
  fun `determinate step with text with fraction`() {
    progressReporterTest(
      ProgressState(text = "p0", fraction = 0.0),
      ProgressState(text = null, fraction = 0.4),
    ) {
      progressStep(endFraction = 0.4, text = "p0") {}
    }
  }

  @Test
  fun `indeterminate steps`() {
    progressReporterTest(
      ProgressState(text = "initial", fraction = -1.0),
      ProgressState(text = null, fraction = -1.0),
      ProgressState(text = "s0", fraction = 0.0),
      ProgressState(text = null, fraction = 0.3),
      ProgressState(text = "between steps", fraction = 0.3),
      ProgressState(text = null, fraction = 0.3),
      ProgressState(text = "s1", fraction = 0.3),
      ProgressState(text = null, fraction = 1.0),
      ProgressState(text = "after last step", fraction = 1.0),
    ) {
      indeterminateStep(text = "initial") {}
      progressStep(endFraction = 0.3, text = "s0") {}
      indeterminateStep(text = "between steps") {}
      progressStep(endFraction = 1.0, text = "s1") {}
      indeterminateStep(text = "after last step") {}
    }
  }

  @Test
  fun `steps with and without text`() {
    progressReporterTest(
      ProgressState(text = "s0", details = null, fraction = 0.0),
      ProgressState(text = null, details = null, fraction = 0.3),
      ProgressState(text = null, details = null, fraction = 0.8),
      ProgressState(text = "s2", details = null, fraction = 0.8),
    ) {
      progressStep(endFraction = 0.3, text = "s0") {}
      progressStep(endFraction = 0.8) {}
      progressStep(endFraction = 1.0, text = "s2") {}
    }
    progressReporterTest(
      ProgressState(text = null, details = null, fraction = 0.0),
      ProgressState(text = null, details = null, fraction = 0.3),
      ProgressState(text = "s1", details = null, fraction = 0.3),
      ProgressState(text = null, details = null, fraction = 0.8),
    ) {
      progressStep(endFraction = 0.3) {}
      progressStep(endFraction = 0.8, text = "s1") {}
      progressStep(endFraction = 1.0) {}
    }
  }

  @Test
  fun `sequential steps top level`() {
    progressReporterTest(
      ProgressState(text = "s0", fraction = 0.0),
      ProgressState(text = null, fraction = 0.3),
      ProgressState(text = "s1", fraction = 0.3),
      ProgressState(text = null, fraction = 0.8),
      ProgressState(text = "s2", fraction = 0.8),
    ) {
      sequentialTest()
    }
  }

  @Test
  fun `sequential steps inside indeterminate step no text`() {
    progressReporterTest(
      ProgressState(text = "s0", fraction = -1.0),
      ProgressState(text = null, fraction = -1.0),
      ProgressState(text = "s1", fraction = -1.0),
      ProgressState(text = null, fraction = -1.0),
      ProgressState(text = "s2", fraction = -1.0),
      ProgressState(text = null, fraction = -1.0),
    ) {
      indeterminateStep {
        sequentialTest()
      }
    }
  }

  @Test
  fun `sequential steps inside determinate step no text no fraction`() {
    progressReporterTest(
      ProgressState(text = null, fraction = 0.0),
      ProgressState(text = "s0", fraction = 0.0),
      ProgressState(text = null, fraction = 0.3),
      ProgressState(text = "s1", fraction = 0.3),
      ProgressState(text = null, fraction = 0.8),
      ProgressState(text = "s2", fraction = 0.8),
    ) {
      progressStep(endFraction = 1.0) {
        sequentialTest()
      }
    }
  }

  @Test
  fun `sequential steps inside determinate step no text with fraction`() {
    progressReporterTest(
      ProgressState(text = null, fraction = 0.0),
      ProgressState(text = "s0", fraction = 0.0 * 0.7),
      ProgressState(text = null, fraction = 0.3 * 0.7),
      ProgressState(text = "s1", fraction = 0.3 * 0.7),
      ProgressState(text = null, fraction = 0.8 * 0.7),
      ProgressState(text = "s2", fraction = 0.8 * 0.7),
      ProgressState(text = null, fraction = 1.0 * 0.7),
    ) {
      progressStep(endFraction = 0.7) {
        sequentialTest()
      }
    }
  }

  @Test
  fun `sequential steps inside indeterminate step with text`() {
    progressReporterTest(
      ProgressState(text = "outer", details = null, fraction = -1.0),
      ProgressState(text = "outer", details = "s0", fraction = -1.0),
      ProgressState(text = "outer", details = null, fraction = -1.0),
      ProgressState(text = "outer", details = "s1", fraction = -1.0),
      ProgressState(text = "outer", details = null, fraction = -1.0),
      ProgressState(text = "outer", details = "s2", fraction = -1.0),
      ProgressState(text = "outer", details = null, fraction = -1.0),
      ProgressState(text = null, details = null, fraction = -1.0),
    ) {
      indeterminateStep(text = "outer") {
        sequentialTest()
      }
    }
  }

  @Test
  fun `sequential steps inside determinate step with text no fraction`() {
    progressReporterTest(
      ProgressState(text = "outer", details = null, fraction = 0.0),
      ProgressState(text = "outer", details = "s0", fraction = 0.0),
      ProgressState(text = "outer", details = null, fraction = 0.3),
      ProgressState(text = "outer", details = "s1", fraction = 0.3),
      ProgressState(text = "outer", details = null, fraction = 0.8),
      ProgressState(text = "outer", details = "s2", fraction = 0.8),
      ProgressState(text = "outer", details = null, fraction = 1.0),
    ) {
      progressStep(endFraction = 1.0, text = "outer") {
        sequentialTest()
      }
    }
  }

  @Test
  fun `sequential steps inside determinate step with text with fraction`() {
    progressReporterTest(
      ProgressState(text = "outer", details = null, fraction = 0.0),
      ProgressState(text = "outer", details = "s0", fraction = 0.0 * 0.7),
      ProgressState(text = "outer", details = null, fraction = 0.3 * 0.7),
      ProgressState(text = "outer", details = "s1", fraction = 0.3 * 0.7),
      ProgressState(text = "outer", details = null, fraction = 0.8 * 0.7),
      ProgressState(text = "outer", details = "s2", fraction = 0.8 * 0.7),
      ProgressState(text = "outer", details = null, fraction = 1.0 * 0.7),
      ProgressState(text = null, details = null, fraction = 0.7),
    ) {
      progressStep(endFraction = 0.7, text = "outer") {
        sequentialTest()
      }
    }
  }

  private suspend fun sequentialTest() {
    progressStep(endFraction = 0.3, text = "s0") {}
    progressStep(endFraction = 0.8, text = "s1") {}
    progressStep(endFraction = 1.0, text = "s2") {}
  }

  @Test
  fun `concurrent steps no text`() {
    progressReporterTest(
      ProgressState(text = null, fraction = 0.0),
      ProgressState(text = "s0s0", fraction = 0.3 * 0.0),             // s0s0 started
      ProgressState(text = "s1s0", fraction = 0.3 * 0.0 + 0.7 * 0.0), // s1s0 started
      ProgressState(text = "s1s0", fraction = 0.3 * 0.4 + 0.7 * 0.0), // s0s0 finished
      ProgressState(text = "s0s1", fraction = 0.3 * 0.4 + 0.7 * 0.0), // s0s1 started
      ProgressState(text = "s0s1", fraction = 0.3 * 0.4 + 0.7 * 0.5), // s1s0 finished
      ProgressState(text = "s1s1", fraction = 0.3 * 0.4 + 0.7 * 0.5), // s1s1 started
      ProgressState(text = "s1s1", fraction = 0.3 * 1.0 + 0.7 * 0.5), // s0s1 finished
      // s1s1 finished
    ) {
      val step1 = launch {
        durationStep(duration = 0.3, text = null) {
          progressStep(endFraction = 0.4, "s0s0") {
            yield()
          }
          progressStep(endFraction = 1.0, "s0s1") {
            awaitCancellation()
          }
        }
      }
      launch {
        durationStep(duration = 0.7, text = null) {
          progressStep(endFraction = 0.5, text = "s1s0") {
            yield()
          }
          progressStep(endFraction = 1.0, text = "s1s1") {
            step1.cancelAndJoin()
          }
        }
      }
    }
  }

  @Test
  fun `concurrent steps with text`() {
    progressReporterTest(
      ProgressState(text = "s0", details = null, fraction = 0.3 * 0.0),               // s0 started
      ProgressState(text = "s0", details = "s0s0", fraction = 0.3 * 0.0),             // s0s0 started
      ProgressState(text = "s1", details = null, fraction = 0.3 * 0.0 + 0.7 * 0.0),   // s1 started
      ProgressState(text = "s1", details = "s1s0", fraction = 0.3 * 0.0 + 0.7 * 0.0), // s1s0 started
      ProgressState(text = "s0", details = null, fraction = 0.3 * 0.4 + 0.7 * 0.0),   // s0s0 finished
      ProgressState(text = "s0", details = "s0s1", fraction = 0.3 * 0.4 + 0.7 * 0.0), // s0s1 started
      ProgressState(text = "s1", details = null, fraction = 0.3 * 0.4 + 0.7 * 0.5),   // s1s0 finished
      ProgressState(text = "s1", details = "s1s1", fraction = 0.3 * 0.4 + 0.7 * 0.5), // s1s1 started
      ProgressState(text = "s0", details = null, fraction = 0.3 * 1.0 + 0.7 * 0.5),   // s0s1 finished
      ProgressState(text = "s1", details = "s1s1", fraction = 0.3 * 1.0 + 0.7 * 0.5), // s0 finished
      ProgressState(text = "s1", details = null, fraction = 1.0),                     // s1s1 finished
      // s1 finished
    ) {
      val step1 = launch {
        durationStep(duration = 0.3, text = "s0") {
          progressStep(endFraction = 0.4, "s0s0") {
            yield()
          }
          progressStep(endFraction = 1.0, "s0s1") {
            awaitCancellation()
          }
        }
      }
      launch {
        durationStep(duration = 0.7, text = "s1") {
          progressStep(endFraction = 0.5, text = "s1s0") {
            yield()
          }
          progressStep(endFraction = 1.0, text = "s1s1") {
            step1.cancelAndJoin()
          }
        }
      }
    }
  }

  @Test
  fun `ultra nested`() {
    val i0 = "i0"
    val s0 = "s0"

    suspend fun steps(inner: suspend CoroutineScope.() -> Unit) {
      indeterminateStep {
        inner()
      }
      progressStep(endFraction = 0.3) {
        inner()
      }
      indeterminateStep(text = i0) {
        inner()
      }
      progressStep(endFraction = 1.0, text = s0) {
        inner()
      }
    }

    progressReporterTest(
      ProgressState(text = i0, details = null, fraction = -1.0),
      ProgressState(text = null, details = null, fraction = -1.0),
      ProgressState(text = s0, details = null, fraction = -1.0),
      ProgressState(text = null, details = null, fraction = -1.0),
      ProgressState(text = i0, details = null, fraction = -1.0),
      ProgressState(text = null, details = null, fraction = -1.0),
      ProgressState(text = s0, details = null, fraction = -1.0),
      ProgressState(text = null, details = null, fraction = -1.0),
      ProgressState(text = i0, details = null, fraction = -1.0),
      ProgressState(text = i0, details = i0, fraction = -1.0),
      ProgressState(text = i0, details = null, fraction = -1.0),
      ProgressState(text = i0, details = s0, fraction = -1.0),
      ProgressState(text = i0, details = null, fraction = -1.0),
      ProgressState(text = null, details = null, fraction = -1.0),
      ProgressState(text = s0, details = null, fraction = -1.0),
      ProgressState(text = s0, details = i0, fraction = -1.0),
      ProgressState(text = s0, details = null, fraction = -1.0),
      ProgressState(text = s0, details = s0, fraction = -1.0),
      ProgressState(text = s0, details = null, fraction = -1.0),
      ProgressState(text = null, details = null, fraction = -1.0),
      ProgressState(text = null, details = null, fraction = 0.0),
      ProgressState(text = i0, details = null, fraction = 0.0),
      ProgressState(text = null, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.0),
      ProgressState(text = null, details = null, fraction = 0.0),
      ProgressState(text = null, details = null, fraction = 0.027),
      ProgressState(text = i0, details = null, fraction = 0.027),
      ProgressState(text = null, details = null, fraction = 0.027),
      ProgressState(text = s0, details = null, fraction = 0.027),
      ProgressState(text = null, details = null, fraction = 0.09),
      ProgressState(text = i0, details = null, fraction = 0.09),
      ProgressState(text = i0, details = i0, fraction = 0.09),
      ProgressState(text = i0, details = null, fraction = 0.09),
      ProgressState(text = i0, details = s0, fraction = 0.09),
      ProgressState(text = i0, details = null, fraction = 0.09),
      ProgressState(text = null, details = null, fraction = 0.09),
      ProgressState(text = s0, details = null, fraction = 0.09),
      ProgressState(text = s0, details = null, fraction = 0.153),
      ProgressState(text = s0, details = i0, fraction = 0.153),
      ProgressState(text = s0, details = null, fraction = 0.153),
      ProgressState(text = s0, details = s0, fraction = 0.153),
      ProgressState(text = s0, details = null, fraction = 0.3),
      ProgressState(text = null, details = null, fraction = 0.3),
      ProgressState(text = i0, details = null, fraction = 0.3),
      ProgressState(text = i0, details = i0, fraction = 0.3),
      ProgressState(text = i0, details = null, fraction = 0.3),
      ProgressState(text = i0, details = s0, fraction = 0.3),
      ProgressState(text = i0, details = null, fraction = 0.3),
      ProgressState(text = i0, details = i0, fraction = 0.3),
      ProgressState(text = i0, details = null, fraction = 0.3),
      ProgressState(text = i0, details = s0, fraction = 0.3),
      ProgressState(text = i0, details = null, fraction = 0.3),
      ProgressState(text = i0, details = i0, fraction = 0.3),
      ProgressState(text = i0, details = null, fraction = 0.3),
      ProgressState(text = i0, details = s0, fraction = 0.3),
      ProgressState(text = i0, details = null, fraction = 0.3),
      ProgressState(text = null, details = null, fraction = 0.3),
      ProgressState(text = s0, details = null, fraction = 0.3),
      ProgressState(text = s0, details = i0, fraction = 0.3),
      ProgressState(text = s0, details = null, fraction = 0.3),
      ProgressState(text = s0, details = s0, fraction = 0.3),
      ProgressState(text = s0, details = null, fraction = 0.3),
      ProgressState(text = s0, details = null, fraction = 0.363),
      ProgressState(text = s0, details = i0, fraction = 0.363),
      ProgressState(text = s0, details = null, fraction = 0.363),
      ProgressState(text = s0, details = s0, fraction = 0.363),
      ProgressState(text = s0, details = null, fraction = 0.51),
      ProgressState(text = s0, details = i0, fraction = 0.51),
      ProgressState(text = s0, details = null, fraction = 0.51),
      ProgressState(text = s0, details = s0, fraction = 0.51),
      ProgressState(text = s0, details = s0, fraction = 0.657),
      ProgressState(text = s0, details = s0, fraction = 1.0),
      ProgressState(text = s0, details = null, fraction = 1.0),
    ) {
      steps {
        steps {
          steps {}
        }
      }
    }
  }

  @Test
  fun `ultra nested concurrent`() {
    val i0 = "i0"
    val s0 = "s0"

    suspend fun concurrentSteps(inner: suspend CoroutineScope.() -> Unit) {
      coroutineScope {
        launch {
          indeterminateStep {
            inner()
          }
        }
        launch {
          durationStep(duration = 0.6, text = null) {
            inner()
          }
        }
        launch {
          indeterminateStep(text = i0) {
            inner()
          }
        }
        launch {
          durationStep(duration = 0.4, text = s0) {
            inner()
          }
        }
      }
    }

    progressReporterTest(
      ProgressState(text = null, details = null, fraction = 0.0),
      ProgressState(text = i0, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.0),
      ProgressState(text = i0, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.0),
      ProgressState(text = i0, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.0),
      ProgressState(text = i0, details = i0, fraction = 0.0),
      ProgressState(text = i0, details = s0, fraction = 0.0),
      ProgressState(text = s0, details = i0, fraction = 0.0),
      ProgressState(text = s0, details = s0, fraction = 0.0),
      ProgressState(text = i0, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.0),
      ProgressState(text = i0, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.0),
      ProgressState(text = i0, details = i0, fraction = 0.0),
      ProgressState(text = i0, details = s0, fraction = 0.0),
      ProgressState(text = s0, details = i0, fraction = 0.0),
      ProgressState(text = s0, details = s0, fraction = 0.0),
      ProgressState(text = i0, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.0),
      ProgressState(text = i0, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.0),
      ProgressState(text = i0, details = i0, fraction = 0.0),
      ProgressState(text = i0, details = s0, fraction = 0.0),
      ProgressState(text = s0, details = i0, fraction = 0.0),
      ProgressState(text = s0, details = s0, fraction = 0.0),
      ProgressState(text = i0, details = i0, fraction = 0.0),
      ProgressState(text = i0, details = s0, fraction = 0.0),
      ProgressState(text = i0, details = i0, fraction = 0.0),
      ProgressState(text = i0, details = s0, fraction = 0.0),
      ProgressState(text = s0, details = i0, fraction = 0.0),
      ProgressState(text = s0, details = s0, fraction = 0.0),
      ProgressState(text = s0, details = i0, fraction = 0.0),
      ProgressState(text = s0, details = s0, fraction = 0.0),
      ProgressState(text = i0, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.0),
      ProgressState(text = s0, details = null, fraction = 0.216),
      ProgressState(text = s0, details = s0, fraction = 0.36),
      ProgressState(text = i0, details = null, fraction = 0.36),
      ProgressState(text = s0, details = s0, fraction = 0.504),
      ProgressState(text = s0, details = null, fraction = 0.6),
      ProgressState(text = s0, details = s0, fraction = 0.744),
      ProgressState(text = s0, details = s0, fraction = 0.84),
      ProgressState(text = s0, details = s0, fraction = 0.9359999999999999),
      ProgressState(text = s0, details = s0, fraction = 1.0),
      ProgressState(text = i0, details = null, fraction = 1.0),
      ProgressState(text = s0, details = null, fraction = 1.0),
    ) {
      concurrentSteps {
        concurrentSteps {
          concurrentSteps { yield() }
        }
      }
    }
  }

  @Test
  fun `raw step contracts`() {
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        indeterminateStep {}
        checkNotNull(progressReporter).rawReporter()
      }
    }
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        indeterminateStep {}
        progressStep(endFraction = 1.0) {}
        checkNotNull(progressReporter).rawReporter()
      }
    }
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        indeterminateStep {}
        durationStep(0.1) {}
        checkNotNull(progressReporter).rawReporter()
      }
    }
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        progressStep(endFraction = 1.0) {}
        indeterminateStep {}
        checkNotNull(progressReporter).rawReporter()
      }
    }
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        durationStep(0.1) {}
        indeterminateStep {}
        checkNotNull(progressReporter).rawReporter()
      }
    }
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        checkNotNull(progressReporter).rawReporter()
        indeterminateStep {}
      }
    }
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        checkNotNull(progressReporter).rawReporter()
        progressStep(endFraction = 1.0) {}
      }
    }
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        checkNotNull(progressReporter).rawReporter()
        durationStep(0.1) {}
      }
    }
    assertLogThrows<IllegalStateException> {
      progressReporterTest {
        checkNotNull(progressReporter).rawReporter()
        checkNotNull(progressReporter).rawReporter()
      }
    }
  }

  @Test
  fun `raw step`() {
    progressReporterTest(
      ProgressState(text = null, details = null, fraction = 1.0),
      ProgressState(text = null, details = "ud", fraction = 1.0),
      ProgressState(text = "ut", details = "ud", fraction = 1.0),
      ProgressState(text = "ut", details = "ud", fraction = 0.5),
      ProgressState(text = null, details = "ud", fraction = 0.5),
      ProgressState(text = null, details = "ud", fraction = -1.0),
    ) {
      rawTest()
    }
  }

  @Test
  fun `raw step inside indeterminate step no text`() {
    progressReporterTest(
      ProgressState(text = null, details = "ud", fraction = -1.0),
      ProgressState(text = "ut", details = "ud", fraction = -1.0),
      ProgressState(text = null, details = "ud", fraction = -1.0),
      ProgressState(text = null, details = null, fraction = -1.0),
    ) {
      indeterminateStep {
        rawTest()
      }
    }
  }

  @Test
  fun `raw step inside determinate step no text no fraction`() {
    progressReporterTest(
      ProgressState(text = null, details = null, fraction = 0.0),
      ProgressState(text = null, details = null, fraction = 1.0),
      ProgressState(text = null, details = "ud", fraction = 1.0),
      ProgressState(text = "ut", details = "ud", fraction = 1.0),
      ProgressState(text = "ut", details = "ud", fraction = 0.5),
      ProgressState(text = null, details = "ud", fraction = 0.5),
      ProgressState(text = null, details = "ud", fraction = 0.0),
    ) {
      progressStep(endFraction = 1.0) {
        rawTest()
      }
    }
  }

  @Test
  fun `raw step inside determinate step no text with fraction`() {
    progressReporterTest(
      ProgressState(text = null, details = null, fraction = 0.0),
      ProgressState(text = null, details = null, fraction = 1.0 * 0.7),
      ProgressState(text = null, details = "ud", fraction = 1.0 * 0.7),
      ProgressState(text = "ut", details = "ud", fraction = 1.0 * 0.7),
      ProgressState(text = "ut", details = "ud", fraction = 0.5 * 0.7),
      ProgressState(text = null, details = "ud", fraction = 0.5 * 0.7),
      ProgressState(text = null, details = "ud", fraction = 0.0 * 0.7),
      ProgressState(text = null, details = null, fraction = 0.7),
    ) {
      progressStep(endFraction = 0.7) {
        rawTest()
      }
    }
  }

  @Test
  fun `raw step inside indeterminate step with text`() {
    progressReporterTest(
      ProgressState(text = "outer", details = null, fraction = -1.0),
      ProgressState(text = "outer", details = "ut", fraction = -1.0),
      ProgressState(text = "outer", details = null, fraction = -1.0),
      ProgressState(text = null, details = null, fraction = -1.0),
    ) {
      indeterminateStep(text = "outer") {
        rawTest()
      }
    }
  }

  @Test
  fun `raw step inside determinate step with text no fraction`() {
    progressReporterTest(
      ProgressState(text = "outer", details = null, fraction = 0.0),
      ProgressState(text = "outer", details = null, fraction = 1.0),
      ProgressState(text = "outer", details = "ut", fraction = 1.0),
      ProgressState(text = "outer", details = "ut", fraction = 0.5),
      ProgressState(text = "outer", details = null, fraction = 0.5),
      ProgressState(text = "outer", details = null, fraction = 0.0),
    ) {
      progressStep(endFraction = 1.0, text = "outer") {
        rawTest()
      }
    }
  }

  @Test
  fun `raw step inside determinate step with text with fraction`() {
    progressReporterTest(
      ProgressState(text = "outer", details = null, fraction = 0.0),
      ProgressState(text = "outer", details = null, fraction = 1.0 * 0.7),
      ProgressState(text = "outer", details = "ut", fraction = 1.0 * 0.7),
      ProgressState(text = "outer", details = "ut", fraction = 0.5 * 0.7),
      ProgressState(text = "outer", details = null, fraction = 0.5 * 0.7),
      ProgressState(text = "outer", details = null, fraction = 0.0 * 0.7),
      ProgressState(text = null, details = null, fraction = 0.7),
    ) {
      progressStep(endFraction = 0.7, text = "outer") {
        rawTest()
      }
    }
  }

  private suspend fun rawTest() {
    withRawProgressReporter {
      check(progressReporter == null)
      val raw = checkNotNull(rawProgressReporter)
      raw.fraction(1.0)
      raw.details("ud") // can set details without text
      raw.text("ut")
      raw.fraction(0.5) // can go back
      raw.text(null) // clearing the text does not clear details
      raw.fraction(null) // can become indeterminate after being determinate
    }
  }

  @Test
  fun `rounding error`() {
    for (total in 1..Int.MAX_VALUE) {
      val duration: Double = 1.0 / total
      val nextToLastDuration = (total - 1) * duration
      assertTrue(nextToLastDuration < 1.0) {
        "Total: $total; next to last: $nextToLastDuration"
      }
      val lastDuration = nextToLastDuration + duration
      if (lastDuration <= 1.0) {
        // we don't care for underflow, for example total=3
        return
      }
      assertTrue(lastDuration < 1.0 + ACCEPTABLE_FRACTION_OVERFLOW) {
        "Total: $total; last: $lastDuration"
      }
    }
  }

  @Test
  fun `rounding error in use`() {
    val total = 93 // a number found during testing, has big rounding error
    progressReporterTest(
      ProgressState(text = null, details = null, fraction = 0.0),
      ProgressState(text = null, details = null, fraction = 0.9892473118279571),
    ) {
      val duration = total.itemDuration()
      val nextToLast = (total - 1) * duration
      progressStep(endFraction = nextToLast) {} // emulate 92 steps
      durationStep(duration = duration) {} // this failed before changes
      val t = assertLogThrows<IllegalStateException> {
        durationStep(duration = duration) { fail() }
      }
      assertTrue(t.message!!.contains("Total duration must not exceed 1.0, duration:")) {
        t.message
      }
    }
  }
}

internal fun progressReporterTest(
  vararg expectedUpdates: ProgressState,
  action: suspend CoroutineScope.() -> Unit,
) = timeoutRunBlocking {
  val actualUpdates = ContainerUtil.createConcurrentList<ProgressState>()
  val progressReporter = TextDetailsProgressReporter(this)
  val collector = launch(Dispatchers.Unconfined + CoroutineName("state collector")) {
    progressReporter.progressState.collect { state ->
      actualUpdates.add(state)
    }
  }
  withContext(progressReporter.asContextElement(), action)
  progressReporter.close()
  progressReporter.awaitCompletion()
  collector.cancelAndJoin()
  assertEquals(ProgressState(null, null, -1.0), actualUpdates.first())
  assertEquals(ProgressState(null, null, 1.0), actualUpdates.last())
  assertOrderedEquals(actualUpdates.toList().init().tail(), expectedUpdates.toList())
}
