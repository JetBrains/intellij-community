// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestDataPath
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.idea.devkit.inspections.quickfix.MismatchedLightServiceLevelAndCtorInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mismatchedLightServiceLevelAndCtor")
class KtMismatchedLightServiceLevelAndCtorInspectionTest : MismatchedLightServiceLevelAndCtorInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/mismatchedLightServiceLevelAndCtor/"

  override fun getFileExtension() = "kt"

  fun testMakeProjectLevel1() {
    doTest(annotateAsServiceFixName)
  }

  fun testMakeProjectLevel2() {
    doTest(annotateAsServiceFixName)
  }

  fun testRemoveProjectParam() {
    doTest(QuickFixBundle.message("remove.parameter.from.usage.text", 1, "parameter", "constructor", "MyService"))
  }

  fun testChangeParamToCoroutineScope() {
    doTest(
      QuickFixBundle.message("change.parameter.from.usage.text", 1, "parameter", "constructor", "MyService", Project::class.java.simpleName,
                             CoroutineScope::class.java.simpleName))
  }
}
