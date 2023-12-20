// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/simplifiableServiceRetrieving")
internal class SimplifiableServiceRetrievingInspectionTest : SimplifiableServiceRetrievingInspectionTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/simplifiableServiceRetrieving/"

  override fun getFileExtension() = "java"

  fun testReplaceWithGetInstanceApplicationLevel() {
    doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyService", "getInstance"))
  }

  fun testReplaceWithGetInstanceProjectLevel() {
    doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyService", "getInstance"))
  }

  fun testTooGenericGetInstanceReturnType() {
    doTest()
  }

  fun testNullableGetInstanceMethod() {
    doTest()
  }
}
