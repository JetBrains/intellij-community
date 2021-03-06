// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/propertyQuotesValidation/")
class DevKitPropertiesQuotesValidationInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/propertyQuotesValidation"
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(DevKitPropertiesQuotesValidationInspection())
  }

  fun testSimple() {
    myFixture.configureByFile("simple.properties")
    myFixture.checkHighlighting()
  }
}