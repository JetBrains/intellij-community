// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil


@TestDataPath("\$CONTENT_ROOT/testData/inspections/actionPresentationInstantiatedInCtor")
internal class ActionPresentationInstantiatedInCtorInspectionTest : ActionPresentationInstantiatedInCtorInspectionTestBase() {

  override fun getBasePath(): String = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/actionPresentationInstantiatedInCtor/"
  override fun getFileExtension(): String = "java"

  override fun setUp() {
    super.setUp()
    setPluginXml("plugin.xml")
  }

  fun testDefaultCtorNegative() { doTest() }

  fun testDefaultCtorPositive() { doTest() }

  fun testThisCall() { doTest() }

  fun testUnregisteredAction() { doTest() }

  fun testNewExpressionNegative() { doTest() }

  fun testNewExpressionPositive() { doTest() }
}