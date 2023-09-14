// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.internal

import org.jetbrains.idea.devkit.inspections.internal.FileEqualsUsageInspectionTestBase

class KtFileEqualsUsageInspectionTest : FileEqualsUsageInspectionTestBase() {
  fun testEquals() {
    doTest("equals(java.io.File(\"any\"))", true)
  }

  fun testCompareTo() {
    doTest("compareTo(java.io.File(\"any\"))", true)
  }

  fun testHashCode() {
    doTest("hashCode()", true)
  }

  fun testGetName() {
    doTest("getName()", false)
  }

  override fun doTest(expectedMethodExpression: String) {
    myFixture.configureByText("Testing.kt", """
      class Testing {
        fun method() {
           val file = java.io.File("any")
           file.$expectedMethodExpression
        }
      }""")
    myFixture.testHighlighting()
  }

  fun testFilesWithEqualsOperator() {
    doOperatorTest("file1", "==", "file2", true)
  }

  fun testFilesWithNotEqualsOperator() {
    doOperatorTest("file1", "!=", "file2", true)
  }

  fun testFilesWithGreaterOperator() {
    doOperatorTest("file1", ">", "file2", true)
  }

  fun testFilesWithGreaterOrEqualOperator() {
    doOperatorTest("file1", ">=", "file2", true)
  }

  fun testFilesWithLessOperator() {
    doOperatorTest("file1", "<", "file2", true)
  }

  fun testFilesWithLessOrEqualOperator() {
    doOperatorTest("file1", "<=", "file2", true)
  }

  // identity should not be reported:
  fun testFilesWithIdentityEqualsOperator() {
    doOperatorTest("file1", "===", "file2", false)
  }

  fun testFilesWithIdentityNotEqualsOperator() {
    doOperatorTest("file1", "!==", "file2", false)
  }

  // null comparisons should not be reported:
  fun testFilesWithLeftOperandNull() {
    doOperatorTest("null", "==", "file2", false)
  }

  fun testFilesWithRightOperandNull() {
    doOperatorTest("null", "!=", "file2", false)
  }

  private fun doOperatorTest(leftOperandText: String, operatorText: String, rightOperandText: String, highlightError: Boolean) {
    val expectedOperatorExpression = getOperatorText(operatorText, highlightError)
    myFixture.configureByText("Testing.kt", """
      import java.io.File
      class Testing {
        fun method() {
           @Suppress("UNUSED_VARIABLE") val file1: File? = File("any")
           @Suppress("UNUSED_VARIABLE") val file2: File? = File("any")
           @Suppress("UNSAFE_OPERATOR_CALL") ($leftOperandText $expectedOperatorExpression $rightOperandText)
        }
      }""")
    myFixture.testHighlighting()
  }
}
