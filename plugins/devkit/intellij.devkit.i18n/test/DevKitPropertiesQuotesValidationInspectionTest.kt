// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("/inspections/propertyMessageValidation/")
@TestApplication
class DevKitPropertiesQuotesValidationInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture, DevkitI18nTestUtil.TESTDATA_PATH + "inspections/propertyMessageValidation")

  @Throws(Exception::class)
  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(DevKitPropertiesMessageValidationInspection())
  }

  @Test
  fun testSimple() {
    myFixture.configureByFile("simple.properties")
    myFixture.checkHighlighting()
  }
}