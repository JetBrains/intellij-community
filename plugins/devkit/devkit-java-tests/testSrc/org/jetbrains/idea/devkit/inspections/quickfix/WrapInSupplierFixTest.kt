// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.applicationServiceAsStaticFinalFieldOrPropertyFixture
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/wrapInSupplierFix")
@TestApplication
class WrapInSupplierFixTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val appService by applicationServiceAsStaticFinalFieldOrPropertyFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/wrapInSupplierFix")

  private val quickFixName = "Wrap application service in 'java.util.function.Supplier'"

  @Test
  fun testWrapFieldInSupplier() {
    appService.doFixTest(quickFixName)
  }
}
