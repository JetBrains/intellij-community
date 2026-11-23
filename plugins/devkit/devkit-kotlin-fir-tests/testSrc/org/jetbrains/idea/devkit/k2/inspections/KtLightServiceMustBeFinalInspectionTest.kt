// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.openapi.components.Service
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.quickfix.lightServiceMustBeFinalFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightServiceMustBeFinal")
@TestApplication
internal class KtLightServiceMustBeFinalInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val lightService by lightServiceMustBeFinalFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/lightServiceMustBeFinal/")

  private val fixName = QuickFixBundle.message("remove.modifier.fix", "MyService", "open")

  @Test
  fun testMakeNotOpen() { lightService.doTest(fixName) }

  @Test
  fun testAbstractClass() { lightService.doTest(QuickFixBundle.message("remove.annotation.fix.text", Service::class.java.simpleName)) }

  @Test
  fun testInterface() { lightService.doTest(QuickFixBundle.message("remove.annotation.fix.text", Service::class.java.simpleName)) }
}
