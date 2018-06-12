// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.idea.devkit.inspections.UnstableApiUsageInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/unstableApiUsage")
class KtUnstableApiUsageInspectionTest : UnstableApiUsageInspectionTestBase() {
  override fun getBasePath() = "${DevkitKtTestsUtil.TESTDATA_PATH}inspections/unstableApiUsage"

  override fun performAdditionalSetUp() {
    // otherwise assertion in PsiFileImpl ("Access to tree elements not allowed") will not pass
    (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter(VirtualFileFilter.NONE)
  }

  fun testInspection() {
    myFixture.testHighlighting("UnstableElementsTest.kt")
  }
}