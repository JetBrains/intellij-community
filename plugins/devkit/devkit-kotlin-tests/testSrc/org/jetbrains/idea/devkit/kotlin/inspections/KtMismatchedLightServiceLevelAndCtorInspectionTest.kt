// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.MismatchedLightServiceLevelAndCtorInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mismatchedLightServiceLevelAndCtor")
internal class KtMismatchedLightServiceLevelAndCtorInspectionTest : MismatchedLightServiceLevelAndCtorInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/mismatchedLightServiceLevelAndCtor/"

  override fun getFileExtension() = "kt"

  fun testFromAppToProjectLevel() { doTest(annotateAsServiceFixName) }

  fun testFromAppToProjectLevelCoroutineScope() { doTest(annotateAsServiceFixName) }

  fun testFromDefaultToProjectLevel() { doTest(annotateAsServiceFixName) }

  fun testFromAppWrappedInArrayToProjectLevel() { doTest(annotateAsServiceFixName) }

  fun testFromEmptyArrayToProjectLevel() { doTest(annotateAsServiceFixName) }

  fun testRemoveProjectParam() { doTest(QuickFixBundle.message("remove.parameter.from.usage.text", 1, "parameter", "constructor", "MyService")) }

  fun testAppLevelDefaultCtor() { doTest() }

  fun testAppLevelPrimaryNoArgCtor() { doTest() }

  fun testAppLevelSecondaryNoArgCtor() { doTest() }

  fun testAppLevelPrimaryCoroutineScopeCtor() { doTest() }

  fun testAppLevelSecondaryCoroutineScopeCtor() { doTest() }
}
