// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.actionPresentationInstantiatedInCtorFixture
import org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/actionPresentationInstantiatedInCtor")
@TestApplication
internal class KtActionPresentationInstantiatedInCtorInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val actionPresentation by actionPresentationInstantiatedInCtorFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/actionPresentationInstantiatedInCtor/")

  @BeforeEach
  fun setUp() {
    actionPresentation.setPluginXml("plugin.xml")
  }

  @Test
  fun testDefaultCtorNegative() {
    actionPresentation.doTest()
  }

  @Test
  fun testDefaultCtorPositive() {
    actionPresentation.doTest()
  }

  @Test
  fun testPrimaryCtorNegative() {
    actionPresentation.doTest()
  }

  @Test
  fun testPrimaryCtorPositive() {
    actionPresentation.doTest()
  }

  @Test
  fun testSecondaryCtors() {
    actionPresentation.doTest()
  }

  @Test
  fun testUnregisteredAction() {
    actionPresentation.doTest()
  }

  @Test
  fun testCtorCallNegative() {
    actionPresentation.doTest()
  }

  @Test
  fun testCtorCallPositive() {
    actionPresentation.doTest()
  }
}
