// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/unstableApiUsage")
class UnstableApiUsageInspectionTest : UnstableApiUsageInspectionTestBase() {
  override fun getBasePath() = "${DevkitJavaTestsUtil.TESTDATA_PATH}inspections/unstableApiUsage"

  fun testInspection() {
    myFixture.testHighlighting(true, false, false, "UnstableElementsTest.java")
  }
}
