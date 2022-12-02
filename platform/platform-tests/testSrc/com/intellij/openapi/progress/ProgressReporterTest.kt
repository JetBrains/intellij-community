// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.progress.impl.ProgressState
import com.intellij.openapi.progress.impl.TextDetailsProgressReporter
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.init
import com.intellij.util.containers.tail
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProgressReporterTest {

  @Test
  fun empty() {
    progressReporterTest {}
  }

  @Test
  fun `end fraction must be greater than 0 and less or equal to 1`() {
    assertThrows<IllegalArgumentException> {
      progressReporterTest {
        progressStep(endFraction = -0.4) { fail() }
      }
    }
    assertThrows<IllegalArgumentException> {
      progressReporterTest {
        progressStep(endFraction = -0.0) { fail() }
      }
    }
    assertThrows<IllegalArgumentException> {
      progressReporterTest {
        progressStep(endFraction = 0.0) { fail() }
      }
    }
    assertThrows<IllegalArgumentException> {
      progressReporterTest {
        progressStep(endFraction = 1.4) { fail() }
      }
    }
  }

  @Test
  fun `two subsequent steps must not request the same end fraction`() {
    assertThrows<IllegalArgumentException> {
      progressReporterTest {
        progressStep(endFraction = 0.3) {}
        progressStep(endFraction = 0.3) { fail() }
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
      progressStep {}
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
      progressStep(text = "p0") {}
    }
  }

  @Test
  fun `determinate step with text with fraction`() {
    progressReporterTest(
      ProgressState(text = "p0", fraction = 0.0),
      ProgressState(text = null, fraction = 0.4),
    ) {
      progressStep(text = "p0", endFraction = 0.4) {}
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
      progressStep(text = "s0", endFraction = 0.3) {}
      indeterminateStep(text = "between steps") {}
      progressStep(text = "s1") {}
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
      progressStep(text = "s0", endFraction = 0.3) {}
      progressStep(endFraction = 0.8) {}
      progressStep(text = "s2") {}
    }
    progressReporterTest(
      ProgressState(text = null, details = null, fraction = 0.0),
      ProgressState(text = null, details = null, fraction = 0.3),
      ProgressState(text = "s1", details = null, fraction = 0.3),
      ProgressState(text = null, details = null, fraction = 0.8),
    ) {
      progressStep(endFraction = 0.3) {}
      progressStep(text = "s1", endFraction = 0.8) {}
      progressStep {}
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
      progressStep {
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
      progressStep(text = "outer") {
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
      progressStep(text = "outer", endFraction = 0.7) {
        sequentialTest()
      }
    }
  }

  private suspend fun sequentialTest() {
    progressStep(text = "s0", endFraction = 0.3) {}
    progressStep(text = "s1", endFraction = 0.8) {}
    progressStep(text = "s2") {}
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
      val step1 = launchStep(text = null, endFraction = 0.3) {
        progressStep("s0s0", endFraction = 0.4) {
          yield()
        }
        progressStep("s0s1") {
          awaitCancellation()
        }
      }
      launchStep(text = null) {
        progressStep(text = "s1s0", endFraction = 0.5) {
          yield()
        }
        progressStep(text = "s1s1") {
          step1.cancelAndJoin()
        }
      }
    }
  }

  @Test
  fun `concurrent steps with text`() {
    progressReporterTest(
      ProgressState(text = "s0", details = null, fraction = 0.3 * 0.0),               // s0 started
      ProgressState(text = "s1", details = null, fraction = 0.3 * 0.0 + 0.7 * 0.0),   // s1 started
      ProgressState(text = "s0", details = "s0s0", fraction = 0.3 * 0.0 + 0.7 * 0.0), // s0s0 started
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
      val step1 = launchStep(text = "s0", endFraction = 0.3) {
        progressStep("s0s0", endFraction = 0.4) {
          yield()
        }
        progressStep("s0s1") {
          awaitCancellation()
        }
      }
      launchStep(text = "s1") {
        progressStep(text = "s1s0", endFraction = 0.5) {
          yield()
        }
        progressStep(text = "s1s1") {
          step1.cancelAndJoin()
        }
      }
    }
  }

  private fun CoroutineScope.launchStep(text: String?, endFraction: Double? = 1.0, action: suspend CoroutineScope.() -> Unit): Job {
    val reporter = checkNotNull(progressReporter)
    val step = reporter.step(text, endFraction)
    return launch(step.asContextElement()) {
      step.use {
        action()
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
      progressStep(text = s0) {
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
        launchStep(text = null, endFraction = null) {
          inner()
        }
        launchStep(text = null, endFraction = 0.6) {
          inner()
        }
        launchStep(text = i0, endFraction = null) {
          inner()
        }
        launchStep(text = s0) {
          inner()
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
    assertThrows<IllegalStateException> {
      progressReporterTest {
        indeterminateStep {}
        checkNotNull(progressReporter).rawReporter()
      }
    }
    assertThrows<IllegalStateException> {
      progressReporterTest {
        indeterminateStep {}
        progressStep {}
        checkNotNull(progressReporter).rawReporter()
      }
    }
    assertThrows<IllegalStateException> {
      progressReporterTest {
        progressStep {}
        indeterminateStep {}
        checkNotNull(progressReporter).rawReporter()
      }
    }
    assertThrows<IllegalStateException> {
      progressReporterTest {
        checkNotNull(progressReporter).rawReporter()
        indeterminateStep {}
      }
    }
    assertThrows<IllegalStateException> {
      progressReporterTest {
        checkNotNull(progressReporter).rawReporter()
        progressStep {}
      }
    }
    assertThrows<IllegalStateException> {
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
      progressStep {
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
      progressStep(text = "outer") {
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
      progressStep(text = "outer", endFraction = 0.7) {
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
}

private fun progressReporterTest(
  vararg expectedUpdates: ProgressState,
  action: suspend CoroutineScope.() -> Unit,
) = timeoutRunBlocking {
  val actualUpdates = ContainerUtil.createConcurrentList<ProgressState>()
  val progressReporter = TextDetailsProgressReporter(CoroutineScope(Dispatchers.Unconfined))
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
