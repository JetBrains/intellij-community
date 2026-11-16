// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/jComponentDataProvider")
@TestApplication
class JComponentDataProviderInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val dataProvider by jComponentDataProviderFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/jComponentDataProvider/")

  @Test
  fun testNotJComponent() {
    dataProvider.doTest()
  }

  @Test
  fun testMyJComponent() {
    dataProvider.doTest()
  }

  @Test
  fun testMyJComponentUiCompatibleDataProvider() {
    dataProvider.doTest()
  }
}