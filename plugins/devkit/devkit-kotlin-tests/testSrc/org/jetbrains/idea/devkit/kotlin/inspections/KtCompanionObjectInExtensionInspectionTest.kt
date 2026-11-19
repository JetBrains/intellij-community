// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/companionObjectInExtension")
abstract class KtCompanionObjectInExtensionInspectionTest(projectFixture: TestFixture<LightJavaProject>) {
  protected val companionObject by ktCompanionObjectInExtensionFixture(projectFixture,
    DevkitKtTestsUtil.TESTDATA_PATH + "inspections/companionObjectInExtension")

  @Test
  fun testNoHighlighting() {
    companionObject.doTest()
  }

  @Test
  fun testEmptyBlockCompanionObject() {
    companionObject.doTest()
  }

  @Test
  fun testEmptyCompanionObject() {
    companionObject.doTest()
  }

  @Test
  fun testExtensionWithCompanionObject() {
    companionObject.doTest()
  }

  @Test
  fun testExtensionWithLoggerAndConstVal() {
    companionObject.doTest()
  }

  @Test
  fun testExtensionWithInitBlocks() {
    companionObject.doTest()
  }
}
