// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.AnActionInitializesTemplatePresentationInCtorInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/anActionInitializesTemplatePresentationInCtor")
internal class KtAnActionInitializesTemplatePresentationInCtorInspectionTest : AnActionInitializesTemplatePresentationInCtorInspectionTestBase() {

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/anActionInitializesTemplatePresentationInCtor/"
  override fun getFileExtension(): String = "kt"

  override fun setUp() {
    super.setUp()
    setPluginXml("plugin.xml")
  }

  fun testDefaultCtor() { doTest() }

  fun testDeepCalls() { doTest() }

  fun testUnregisteredAction() { doTest() }
}
