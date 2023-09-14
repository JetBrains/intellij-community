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
           val file = java.io.File("any");
           file.$expectedMethodExpression;
        }
      }""")
    myFixture.testHighlighting()
  }
}
