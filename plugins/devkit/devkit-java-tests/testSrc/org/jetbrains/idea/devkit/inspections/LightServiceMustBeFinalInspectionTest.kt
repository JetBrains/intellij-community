// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.openapi.components.Service
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.lightServiceMustBeFinalFixture
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightServiceMustBeFinal")
@TestApplication
internal class LightServiceMustBeFinalInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val lightService by lightServiceMustBeFinalFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/lightServiceMustBeFinal/")

  private val MAKE_FINAL_FIX_NAME = QuickFixBundle.message("add.modifier.fix", "MyService", "final")

  @Test
  fun testMakeFinal() { lightService.doTest(MAKE_FINAL_FIX_NAME) }

  @Test
  fun testMakeFinalMultiLineModifierList() { lightService.doTest(MAKE_FINAL_FIX_NAME) }

  @Test
  fun testAbstractClass() { lightService.doTest(QuickFixBundle.message("remove.annotation.fix.text", Service::class.java.simpleName)) }

  @Test
  fun testInterface() { lightService.doTest(QuickFixBundle.message("remove.annotation.fix.text", Service::class.java.simpleName)) }
}
