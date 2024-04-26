// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal

class FileEqualsUsageInspectionTest : FileEqualsUsageInspectionTestBase() {
  fun testEquals() {
    doTest("equals(null)", true)
  }

  fun testCompareTo() {
    doTest("compareTo(null)", true)
  }

  fun testHashCode() {
    doTest("hashCode()", true)
  }

  fun testGetName() {
    doTest("getName()", false)
  }

  override fun doTest(expectedMethodExpression: String) {
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
  fun testFilesWithEqualsOperator() {
    doOperatorTest("file1", "==", "file2", false)
  }

  fun testFilesWithNotEqualOperator() {
    doOperatorTest("file1", "!=", "file2", false)
  }
  fun testFilesWithLeftOperandNull() {
    doOperatorTest("null", "==", "file2", false)
  }

  fun testFilesWithRightOperandNull() {
    doOperatorTest("file1", "!=", "null", false)
  }

  private fun doOperatorTest(leftOperandText: String, operatorText: String, rightOperandText: String,
                             @Suppress("SameParameterValue") highlightError: Boolean) {
    val expectedOperatorExpression = getOperatorText(operatorText, highlightError)
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
