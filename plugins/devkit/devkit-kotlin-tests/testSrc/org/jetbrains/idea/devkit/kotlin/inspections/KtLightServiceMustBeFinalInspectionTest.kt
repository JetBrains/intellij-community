// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.openapi.components.Service
import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.LightServiceMustBeFinalInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightServiceMustBeFinal")
internal class KtLightServiceMustBeFinalInspectionTest : LightServiceMustBeFinalInspectionTestBase() {

  private val fixName = QuickFixBundle.message("remove.modifier.fix", "MyService", "open")

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/lightServiceMustBeFinal/"

  override fun getFileExtension() = "kt"

  fun testMakeNotOpen() { doTest(fixName) }

  fun testAbstractClass() { doTest(QuickFixBundle.message("remove.annotation.fix.text", Service::class.java.simpleName)) }

  fun testInterface() { doTest(QuickFixBundle.message("remove.annotation.fix.text", Service::class.java.simpleName)) }
}
