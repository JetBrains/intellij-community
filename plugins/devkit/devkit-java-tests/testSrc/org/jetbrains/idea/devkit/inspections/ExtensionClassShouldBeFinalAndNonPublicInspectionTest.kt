// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.ExtensionClassShouldBeFinalAndNonPublicInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/extensionClassShouldBeFinalAndNonPublic")
internal class ExtensionClassShouldBeFinalAndNonPublicInspectionTest : ExtensionClassShouldBeFinalAndNonPublicInspectionTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/extensionClassShouldBeFinalAndNonPublic/"
  override fun getFileExtension() = "java"

  override fun setUp() {
    super.setUp()
    myFixture.configureByFile("plugin.xml")
  }

  fun testMakeFinal() {
    doTest("Make 'MyInspection' final")
  }

  fun testMakeNotPublic() {
    doTest("Make 'MakeNotPublic' not public")
  }

  fun testFinalPackagePrivateExtensionClass() {
    doTest()
  }

  fun testHasInheritor() {
    doTest()
  }

  fun testVisibleForTestingAnnotation() {
    doTest()
  }

  fun testProtectedMembers() {
    doTest("Make 'MyInspection' final")
  }

  fun testFinalMethod() {
    doTest("Make 'MyInspection' final")
  }
}