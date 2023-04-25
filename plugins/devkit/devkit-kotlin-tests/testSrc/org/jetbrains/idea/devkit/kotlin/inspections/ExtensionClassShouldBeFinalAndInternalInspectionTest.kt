// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.ExtensionClassShouldBeFinalAndPackagePrivateInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/extensionClassShouldBeFinalAndInternal")
class ExtensionClassShouldBeFinalAndInternalInspectionTest : ExtensionClassShouldBeFinalAndPackagePrivateInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/extensionClassShouldBeFinalAndInternal/"
  override fun getFileExtension(): String = "kt"

  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("resources", "resources")
  }

  fun testMakeNotOpen() {
    doTest("Make 'MyInspection' not open")
  }

  fun testMakeInternal() {
    doTest("Make 'MyInspection' internal")
  }

  fun testInternalFinalExtensionClass() {
    doTest()
  }
}