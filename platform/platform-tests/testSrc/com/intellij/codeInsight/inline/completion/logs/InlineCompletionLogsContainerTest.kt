// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ReflectionUtil
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
      val logsContainer = InlineCompletionLogsContainer(1f)
      logsContainer.add(TestPhasedLogs.basicTestField with 42)
      logsContainer.add(TestPhasedLogs.fullTestField with 1337)

      val logs = FUCollectorTestCase.collectLogEvents("ML", testRootDisposable) {
        logsContainer.logCurrent()
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
      val logsContainer = InlineCompletionLogsContainer(0f)
      logsContainer.add(TestPhasedLogs.basicTestField with 42)
      logsContainer.add(TestPhasedLogs.fullTestField with 1337)

      val logs = FUCollectorTestCase.collectLogEvents("ML", testRootDisposable) {
        logsContainer.logCurrent()
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
      val logsContainer = InlineCompletionLogsContainer(1f)
      logsContainer.forceFullLogs.set(true)
      logsContainer.add(TestPhasedLogs.basicTestField with 42)
      logsContainer.add(TestPhasedLogs.fullTestField with 1337)

      val logs = FUCollectorTestCase.collectLogEvents("ML", testRootDisposable) {
        logsContainer.logCurrent()
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
   * Test that only basic fields are recorded for (eap = false, don't pass the random threshold, not random pass)
   */
  @Test
  fun testFullLogFiltered() {
    withEap(false) {
      val logsContainer = InlineCompletionLogsContainer(1f)
      logsContainer.add(TestPhasedLogs.basicTestField with 42)
      logsContainer.add(TestPhasedLogs.fullTestField with 1337)

      val logs = FUCollectorTestCase.collectLogEvents("ML", testRootDisposable) {
        logsContainer.logCurrent()
      }

      // expect only the basic log
      assertMaps(
        mapOf(
          "inline_api_starting" to mapOf(
            "basic_test_field" to "42"
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
    val logsContainer = InlineCompletionLogsContainer(1f)
    assertThrows(IllegalArgumentException::class.java) {
      logsContainer.add(EventFields.Boolean("incorrect_field").with(false))
    }
  }

  private fun withEap(isEAP: Boolean, action: () -> Unit) {
    val previousState = ApplicationManager.getApplication().isEAP
    val appInfo = ApplicationInfoImpl.getShadowInstance()
    try {
      ReflectionUtil.setField(ApplicationInfoImpl::class.java, appInfo, Boolean::class.java, "myEAP", isEAP)
      action()
    } finally {
      ReflectionUtil.setField(ApplicationInfoImpl::class.java, appInfo, Boolean::class.java, "myEAP", previousState)
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
  val basicTestField = registerBasic(EventFields.Int("basic_test_field"))
  val fullTestField = register(EventFields.Int("full_test_field"))
}

class TestInlineCompletionSessionLogsEP : InlineCompletionSessionLogsEP {
  override val logGroups: List<PhasedLogs> = listOf(TestPhasedLogs)
}