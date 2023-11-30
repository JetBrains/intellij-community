// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.MismatchedLightServiceLevelAndCtorInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mismatchedLightServiceLevelAndCtor")
internal class MismatchedLightServiceLevelAndCtorInspectionTest : MismatchedLightServiceLevelAndCtorInspectionTestBase() {

  private val NO_ARG_CTOR_FIX_NAME = QuickFixBundle.message("change.method.parameters.text", "()")

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/mismatchedLightServiceLevelAndCtor/"

  override fun getFileExtension() = "java"

  fun testFromAppToProjectLevel() { doTest(annotateAsServiceFixName) }

  fun testFromAppToProjectLevelCoroutineScope() { doTest(annotateAsServiceFixName) }

  fun testFromDefaultToProjectLevel() { doTest(annotateAsServiceFixName) }

  fun testFromAppWrappedInArrayToProjectLevel() { doTest(annotateAsServiceFixName) }

  fun testFromEmptyArrayToProjectLevel() { doTest(annotateAsServiceFixName) }

  fun testRemoveProjectParam() { doTest(NO_ARG_CTOR_FIX_NAME) }

  fun testAppLevelDefaultCtor() { doTest() }

  fun testAppLevelNoArgCtor() { doTest() }

  fun testAppLevelCoroutineScopeCtor() { doTest() }
}
