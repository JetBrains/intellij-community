// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.ActionPresentationInstantiatedInCtorInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/actionPresentationInstantiatedInCtor")
internal class KtActionPresentationInstantiatedInCtorInspectionTest : ActionPresentationInstantiatedInCtorInspectionTestBase() {

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/actionPresentationInstantiatedInCtor/"
  override fun getFileExtension(): String = "kt"

  override fun setUp() {
    super.setUp()
    setPluginXml("plugin.xml")
  }

  fun testDefaultCtorNegative() { doTest() }

  fun testDefaultCtorPositive() { doTest() }

  fun testPrimaryCtorNegative() { doTest() }

  fun testPrimaryCtorPositive() { doTest() }

  fun testSecondaryCtors() { doTest() }

  fun testUnregisteredAction() { doTest() }

  fun testCtorCallNegative() { doTest() }

  fun testCtorCallPositive() { doTest() }
}
