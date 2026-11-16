// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.internal

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.internal.fileEqualsUsageFixture
import org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture
import org.junit.jupiter.api.Test

@TestApplication
class KtFileEqualsUsageInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val fileEquals by fileEqualsUsageFixture(projectFixture)
  private val myFixture get() = fileEquals.fixture

  @Test
  fun testEquals() {
    doTest("equals(java.io.File(\"any\"))", true)
  }

  @Test
  fun testCompareTo() {
    doTest("compareTo(java.io.File(\"any\"))", true)
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
    myFixture.configureByText("Testing.kt", """
      class Testing {
        fun method() {
           val file = java.io.File("any")
           file.$expectedMethodExpression
        }
      }""")
    myFixture.testHighlighting()
  }

  @Test
  fun testFilesWithEqualsOperator() {
    doOperatorTest("file1", "==", "file2", true)
  }

  @Test
  fun testFilesWithNotEqualsOperator() {
    doOperatorTest("file1", "!=", "file2", true)
  }

  @Test
  fun testFilesWithGreaterOperator() {
    doOperatorTest("file1", ">", "file2", true)
  }

  @Test
  fun testFilesWithGreaterOrEqualOperator() {
    doOperatorTest("file1", ">=", "file2", true)
  }

  @Test
  fun testFilesWithLessOperator() {
    doOperatorTest("file1", "<", "file2", true)
  }

  @Test
  fun testFilesWithLessOrEqualOperator() {
    doOperatorTest("file1", "<=", "file2", true)
  }

  // identity should not be reported:
  @Test
  fun testFilesWithIdentityEqualsOperator() {
    doOperatorTest("file1", "===", "file2", false)
  }

  @Test
  fun testFilesWithIdentityNotEqualsOperator() {
    doOperatorTest("file1", "!==", "file2", false)
  }

  // null comparisons should not be reported:
  @Test
  fun testFilesWithLeftOperandNull() {
    doOperatorTest("null", "==", "file2", false)
  }

  @Test
  fun testFilesWithRightOperandNull() {
    doOperatorTest("null", "!=", "file2", false)
  }

  private fun doOperatorTest(leftOperandText: String, operatorText: String, rightOperandText: String, highlightError: Boolean) {
    val expectedOperatorExpression = fileEquals.getOperatorText(operatorText, highlightError)
    myFixture.configureByText("Testing.kt", """
      import java.io.File
      class Testing {
        fun method() {
           @Suppress("UNUSED_VARIABLE") val file1: File? = File("any")
           @Suppress("UNUSED_VARIABLE") val file2: File? = File("any")
           @Suppress(<warning descr="[ERROR_SUPPRESSION]">"UNSAFE_OPERATOR_CALL"</warning>) ($leftOperandText $expectedOperatorExpression $rightOperandText)
        }
      }""")
    myFixture.testHighlighting()
  }
}
