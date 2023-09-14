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
}
