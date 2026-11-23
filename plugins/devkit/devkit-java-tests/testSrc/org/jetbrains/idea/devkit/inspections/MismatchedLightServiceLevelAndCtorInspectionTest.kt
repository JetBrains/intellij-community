// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.mismatchedLightServiceLevelAndCtorFixture
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mismatchedLightServiceLevelAndCtor")
@TestApplication
internal class MismatchedLightServiceLevelAndCtorInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val mismatchedLevel by mismatchedLightServiceLevelAndCtorFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/mismatchedLightServiceLevelAndCtor/")

  private val NO_ARG_CTOR_FIX_NAME = QuickFixBundle.message("change.method.parameters.text", "()")

  @Test
  fun testFromAppToProjectLevel() { mismatchedLevel.doTest(mismatchedLevel.annotateAsServiceFixName) }

  @Test
  fun testFromAppToProjectLevelCoroutineScope() { mismatchedLevel.doTest(mismatchedLevel.annotateAsServiceFixName) }

  @Test
  fun testFromDefaultToProjectLevel() { mismatchedLevel.doTest(mismatchedLevel.annotateAsServiceFixName) }

  @Test
  fun testFromAppWrappedInArrayToProjectLevel() { mismatchedLevel.doTest(mismatchedLevel.annotateAsServiceFixName) }

  @Test
  fun testFromEmptyArrayToProjectLevel() { mismatchedLevel.doTest(mismatchedLevel.annotateAsServiceFixName) }

  @Test
  fun testRemoveProjectParam() { mismatchedLevel.doTest(NO_ARG_CTOR_FIX_NAME) }

  @Test
  fun testAppLevelDefaultCtor() { mismatchedLevel.doTest() }

  @Test
  fun testAppLevelNoArgCtor() { mismatchedLevel.doTest() }

  @Test
  fun testAppLevelCoroutineScopeCtor() { mismatchedLevel.doTest() }
}
