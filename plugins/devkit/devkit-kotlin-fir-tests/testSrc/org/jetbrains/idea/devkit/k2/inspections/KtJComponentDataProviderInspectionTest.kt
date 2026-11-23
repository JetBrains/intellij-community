// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.jComponentDataProviderFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/jComponentDataProvider")
@TestApplication
class KtJComponentDataProviderInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val dataProvider by jComponentDataProviderFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "/inspections/jComponentDataProvider/")

  @Test
  fun testObject() {
    dataProvider.doTest()
  }

  @Test
  fun testNotJComponent() {
    dataProvider.doTest()
  }

  @Test
  fun testMyJComponent() {
    dataProvider.doTest()
  }

}