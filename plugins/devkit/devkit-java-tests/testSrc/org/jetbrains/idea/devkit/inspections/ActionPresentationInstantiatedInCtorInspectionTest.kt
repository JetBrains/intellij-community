// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/actionPresentationInstantiatedInCtor")
@TestApplication
internal class ActionPresentationInstantiatedInCtorInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val actionPresentation by actionPresentationInstantiatedInCtorFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/actionPresentationInstantiatedInCtor/")

  @BeforeEach
  fun setUp() {
    actionPresentation.setPluginXml("plugin.xml")
  }

  @Test
  fun testDefaultCtorNegative() { actionPresentation.doTest() }

  @Test
  fun testDefaultCtorPositive() { actionPresentation.doTest() }

  @Test
  fun testThisCall() { actionPresentation.doTest() }

  @Test
  fun testUnregisteredAction() { actionPresentation.doTest() }

  @Test
  fun testNewExpressionNegative() { actionPresentation.doTest() }

  @Test
  fun testNewExpressionPositive() { actionPresentation.doTest() }
}