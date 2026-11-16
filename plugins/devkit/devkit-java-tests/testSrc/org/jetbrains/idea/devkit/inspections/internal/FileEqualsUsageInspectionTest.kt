// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture
import org.junit.jupiter.api.Test

@TestApplication
class FileEqualsUsageInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val fileEquals by fileEqualsUsageFixture(projectFixture)
  private val myFixture get() = fileEquals.fixture

  @Test
  fun testEquals() {
    doTest("equals(null)", true)
  }

  @Test
  fun testCompareTo() {
    doTest("compareTo(null)", true)
  }

  @Test
  fun testHashCode() {
    doTest("hashCode()", true)
  }

  @Test
  fun testGetName() {
    doTest("getName()", false)
  }

  private fun doTest(methodExpressionText: String, highlightError: Boolean) {
    doTest(fileEquals.getMethodExpressionText(methodExpressionText, highlightError))
  }

  private fun doTest(expectedMethodExpression: String) {
    myFixture.configureByText("Testing.java", """
        public class Testing {
          public void method() {
             java.io.File file = null;
             file.$expectedMethodExpression;
          }
        }""")
    myFixture.testHighlighting()
  }

  // identify comparisons should not be reported in Java:
  @Test
  fun testFilesWithEqualsOperator() {
    doOperatorTest("file1", "==", "file2", false)
  }

  @Test
  fun testFilesWithNotEqualOperator() {
    doOperatorTest("file1", "!=", "file2", false)
  }
  @Test
  fun testFilesWithLeftOperandNull() {
    doOperatorTest("null", "==", "file2", false)
  }

  @Test
  fun testFilesWithRightOperandNull() {
    doOperatorTest("file1", "!=", "null", false)
  }

  private fun doOperatorTest(leftOperandText: String, operatorText: String, rightOperandText: String,
                             @Suppress("SameParameterValue") highlightError: Boolean) {
    val expectedOperatorExpression = fileEquals.getOperatorText(operatorText, highlightError)
    myFixture.configureByText("Testing.java", """
      import java.io.File;
      class Testing {
        boolean method() {
           File file1 = new File("any");
           File file2 = new File("any");
           return $leftOperandText $expectedOperatorExpression $rightOperandText;
        }
      }""")
    myFixture.testHighlighting()
  }
}
