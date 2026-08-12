// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEapSupport
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [InlineCompletionLogsContainer.logCurrent] logic
 */
@RunWith(JUnit4::class)
class InlineCompletionLogsContainerTest : LightPlatformTestCase() {

  public override fun setUp() {
    super.setUp()

    ExtensionTestUtil.addExtensions(
      InlineCompletionSessionLogsEP.EP_NAME,
      listOf(TestInlineCompletionSessionLogsEP()),
      testRootDisposable)
  }

  /**
   * Test for both basic and full logs are recorded (eap = true)
   */
  @Test
  fun testEapLogs() {
    withEap(true) {
      val logsContainer = InlineCompletionLogsContainer()
      logsContainer.mockRandom(1f)
      logsContainer.add(TestPhasedLogs.basicTestField with 42)
      logsContainer.add(TestPhasedLogs.fullTestField with 1337)

      val logs = FUCollectorTestCase.collectLogEvents(recorder = "ML", parentDisposable = testRootDisposable, escapeChars = true) {
        logsContainer.logCurrent(project = null)
      }

      // expect both logs
      assertMaps(
        mapOf(
          "inline_api_starting" to mapOf(
            "basic_test_field" to "42",
            "full_test_field" to "1337",
          )
        ),
        logs.first().event.data
      )
    }
  }

  /**
   * Test for both basic and full logs are recorded (eap = false, pass random threshold)
   */
  @Test
  fun testFullLogsForRelease() {
    withEap(false) {
      val logsContainer = InlineCompletionLogsContainer()
      logsContainer.mockRandom(0f)
      logsContainer.add(TestPhasedLogs.basicTestField with 42)
      logsContainer.add(TestPhasedLogs.fullTestField with 1337)

      val logs = FUCollectorTestCase.collectLogEvents(recorder = "ML", parentDisposable = testRootDisposable, escapeChars = true) {
        logsContainer.logCurrent(project = null)
      }

      // expect both logs
      assertMaps(
        mapOf(
          "inline_api_starting" to mapOf(
            "basic_test_field" to "42",
            "full_test_field" to "1337",
          )
        ),
        logs.first().event.data
      )
    }
  }

  /**
   * Test for both basic and full logs are recorded (eap = false, don't pass the random threshold, random pass)
   */
  @Test
  fun testFullLogsForReleaseRandomPass() {
    withEap(false) {
      val logsContainer = InlineCompletionLogsContainer()
      logsContainer.mockRandom(1f)
      logsContainer.forceFullLogs()
      logsContainer.add(TestPhasedLogs.basicTestField with 42)
      logsContainer.add(TestPhasedLogs.fullTestField with 1337)

      val logs = FUCollectorTestCase.collectLogEvents(recorder = "ML", parentDisposable = testRootDisposable, escapeChars = true) {
        logsContainer.logCurrent(project = null)
      }

      // expect both logs
      assertMaps(
        mapOf(
          "inline_api_starting" to mapOf(
            "basic_test_field" to "42",
            "full_test_field" to "1337",
          )
        ),
        logs.first().event.data
      )
    }
  }
  /**
   * Fields which are not registered for a specific phase are not allowed.
   */
  @Test
  fun testPhaseNotFoundForField() {
    val logsContainer = InlineCompletionLogsContainer()
    logsContainer.mockRandom(1f)
    assertThrows(IllegalArgumentException::class.java) {
      logsContainer.add(EventFields.Boolean("incorrect_field").with(false))
    }
  }

  /**
   * The same field added several times per session (e.g. repeated postprocessing passes or `updateLatestLogs`)
   * must not accumulate into duplicate logs: the container keeps a single entry with the latest value.
   * Two logs with the same field name in one phase would otherwise break mlapi feature selection.
   */
  @Test
  fun testDuplicateFieldKeepsOnlyLatest(): Unit = timeoutRunBlocking {
    val logsContainer = InlineCompletionLogsContainer()
    logsContainer.mockRandom(1f)
    logsContainer.add(TestPhasedLogs.basicTestField with 42)
    logsContainer.add(TestPhasedLogs.basicTestField with 99)

    val phased = logsContainer.awaitAndGetCurrentLogsPhased()
      .getValue(Phase.INLINE_API_STARTING)
      .filter { it.field.name == "basic_test_field" }
    assertEquals("Duplicate field must not accumulate", 1, phased.size)
    assertEquals("The latest value must be kept", 99, phased.single().data)

    val flat = logsContainer.awaitAndGetCurrentLogs().filter { it.field.name == "basic_test_field" }
    assertEquals("Duplicate field must not accumulate", 1, flat.size)
    assertEquals("The latest value must be kept", 99, flat.single().data)
  }

  /**
   * The value logged for a field written twice is the latest one.
   */
  @Test
  fun testDuplicateFieldLogsLatestValue() {
      val logsContainer = InlineCompletionLogsContainer()
      logsContainer.mockRandom(1f)
      logsContainer.add(TestPhasedLogs.basicTestField with 42)
      logsContainer.add(TestPhasedLogs.basicTestField with 99)

      val logs = FUCollectorTestCase.collectLogEvents(recorder = "ML", parentDisposable = testRootDisposable, escapeChars = true) {
        logsContainer.logCurrent(project = null)
      }

      assertMaps(
        mapOf(
          "inline_api_starting" to mapOf(
            "basic_test_field" to "99",
          )
        ),
        logs.first().event.data
      )
  }

  /**
   * The container lives in the editor user data and is captured by async logging jobs running on an application-level scope,
   * so it must never hold a strong reference to a project: the project is passed to [InlineCompletionLogsContainer.logCurrent]
   * instead. See LLM-17026.
   */
  @Test
  fun testContainerDoesNotRetainProject() {
    val logsContainer = InlineCompletionLogsContainer()
    logsContainer.mockRandom(1f)
    // Only a primitive field and no `addAsync` on purpose: `DebugReflectionUtil.isTrivial` treats only primitives, strings and
    // arrays as trivial, so an `EventFields.Class` value would make LeakHunter walk the statics of the logged class, and a job
    // left in `asyncAdds` would open a path into the application-level scope.
    logsContainer.add(TestPhasedLogs.basicTestField with 42)

    val logs = FUCollectorTestCase.collectLogEvents(recorder = "ML", parentDisposable = testRootDisposable, escapeChars = true) {
      logsContainer.logCurrent(project)
    }

    assertNotNull("The project must still be reported in the FUS event", logs.first().event.data["project"])
    LeakHunter.checkLeak(logsContainer, Project::class.java)
  }

  /**
   * A canceled awaiter must not drop a still running async add from the cancellation registry:
   * [InlineCompletionLogsContainer.logCurrent] must still be able to cancel it, otherwise the add keeps running on the
   * application-level scope and retains everything its block captured. See LLM-17026.
   */
  @Test
  fun testCanceledAwaitDoesNotStrandAsyncAdd(): Unit = timeoutRunBlocking {
    val logsContainer = InlineCompletionLogsContainer()
    logsContainer.mockRandom(1f)
    val started = CompletableDeferred<Unit>()
    val blockCanceled = CompletableDeferred<Unit>()
    logsContainer.addAsync {
      started.complete(Unit)
      try {
        awaitCancellation()
      }
      finally {
        blockCanceled.complete(Unit)
      }
    }
    started.await()

    try {
      // `Dispatchers.Unconfined` runs the awaiter eagerly on this thread up to its first real suspension,
      // so it is guaranteed to be inside `join` by the time `launch` returns.
      val awaiter = launch(Dispatchers.Unconfined) { logsContainer.awaitAndGetCurrentLogs() }
      awaiter.cancelAndJoin()
    }
    finally {
      logsContainer.logCurrent(project = null)
    }
    blockCanceled.await()
  }

  private fun withEap(isEAP: Boolean, action: () -> Unit) {
    try {
      InlineCompletionEapSupport.getInstance().setMockEap(isEAP)
      action()
    } finally {
      InlineCompletionEapSupport.getInstance().clearMock()
    }
  }

  private fun assertDeepEquals(expected: Any, actual: Any) {
    if (expected is Map<*,*> && actual is Map<*,*>) {
      assertMaps(expected as Map<String, *>, actual as Map<String, *>);
    } else {
      // we expect only primitive types otherwise
      assertEquals(expected.toString(), actual.toString());
    }
  }

  private fun assertMaps(expected: Map<String, *>, actual: Map<String, *>) {
    assertEquals("Map sizes do not match", expected.size, actual.size)
    // iterate over keys from both maps
    expected.keys.sorted().zip(actual.keys.sorted()).forEach {
      assertEquals("Keys do not match $it", it.first, it.second)
      // check if the values both present or missing
      val expectedValue = expected[it.first]
      val actualValue = actual[it.second]
      if (expectedValue != null && actualValue != null) {
        assertDeepEquals(expectedValue, actualValue)
      } else {
        assertNull(actualValue)
        assertNull(expectedValue)
      }
    }
  }
}

object TestPhasedLogs : PhasedLogs(Phase.INLINE_API_STARTING) {
  val basicTestField = register(EventFields.Int("basic_test_field"))
  val fullTestField = register(EventFields.Int("full_test_field"))
}

class TestInlineCompletionSessionLogsEP : InlineCompletionSessionLogsEP {
  override val logGroups: List<PhasedLogs> = listOf(TestPhasedLogs)
}