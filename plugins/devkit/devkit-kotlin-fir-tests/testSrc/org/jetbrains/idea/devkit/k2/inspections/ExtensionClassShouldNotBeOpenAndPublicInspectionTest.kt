// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.quickfix.extensionClassShouldBeFinalAndNonPublicFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/extensionClassShouldNotBeOpenAndPublic")
@TestApplication
internal class ExtensionClassShouldNotBeOpenAndPublicInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val extensionClass by extensionClassShouldBeFinalAndNonPublicFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/extensionClassShouldNotBeOpenAndPublic/")
  private val myFixture get() = extensionClass.fixture

  @BeforeEach
  fun setUp() {
    myFixture.configureByFile("plugin.xml")
  }

  @Test
  fun testMakeNotOpen() {
    extensionClass.doTest("Make 'MyInspection' not open")
  }

  @Test
  fun testMakeInternal() {
    extensionClass.doTest("Make 'MyInspection' internal")
  }

  @Test
  fun testInternalFinalExtensionClass() {
    extensionClass.doTest()
  }

  @Test
  fun testHasInheritor() {
    extensionClass.doTest()
  }

  @Test
  fun testVisibleForTestingAnnotation() {
    extensionClass.doTest()
  }
}