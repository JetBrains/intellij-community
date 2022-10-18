package org.jetbrains.completion.full.line

import junit.framework.TestCase
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

class UtilsTestCase : FullLineCompletionTestCase() {
  fun `test getTestName fun - one test in a != ds`() {
    TestCase.assertEquals("GetTestNameFun-OneTestInA!=Ds", getTestName(false))
  }
}
