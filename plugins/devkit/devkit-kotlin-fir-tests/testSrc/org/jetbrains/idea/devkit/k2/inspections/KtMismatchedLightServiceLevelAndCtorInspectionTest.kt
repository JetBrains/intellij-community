// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.quickfix.mismatchedLightServiceLevelAndCtorFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/mismatchedLightServiceLevelAndCtor")
@TestApplication
internal class KtMismatchedLightServiceLevelAndCtorInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val mismatchedLevel by mismatchedLightServiceLevelAndCtorFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/mismatchedLightServiceLevelAndCtor/")

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
  fun testRemoveProjectParam() { mismatchedLevel.doTest(QuickFixBundle.message("remove.parameter.from.usage.text", 1, "parameter", "constructor", "MyService()")) }

  @Test
  fun testAppLevelDefaultCtor() { mismatchedLevel.doTest() }

  @Test
  fun testAppLevelPrimaryNoArgCtor() { mismatchedLevel.doTest() }

  @Test
  fun testAppLevelSecondaryNoArgCtor() { mismatchedLevel.doTest() }

  @Test
  fun testAppLevelPrimaryCoroutineScopeCtor() { mismatchedLevel.doTest() }

  @Test
  fun testAppLevelSecondaryCoroutineScopeCtor() { mismatchedLevel.doTest() }
}
