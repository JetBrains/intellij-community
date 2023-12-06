// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.openapi.components.Service
import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.LightServiceMustBeFinalInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightServiceMustBeFinal")
internal class LightServiceMustBeFinalInspectionTest : LightServiceMustBeFinalInspectionTestBase() {

  private val MAKE_FINAL_FIX_NAME = QuickFixBundle.message("add.modifier.fix", "MyService", "final")
  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/lightServiceMustBeFinal/"

  override fun getFileExtension() = "java"

  fun testMakeFinal() { doTest(MAKE_FINAL_FIX_NAME) }

  fun testMakeFinalMultiLineModifierList() { doTest(MAKE_FINAL_FIX_NAME) }

  fun testAbstractClass() { doTest(QuickFixBundle.message("remove.annotation.fix.text", Service::class.java.simpleName)) }

  fun testInterface() { doTest(QuickFixBundle.message("remove.annotation.fix.text", Service::class.java.simpleName)) }
}
