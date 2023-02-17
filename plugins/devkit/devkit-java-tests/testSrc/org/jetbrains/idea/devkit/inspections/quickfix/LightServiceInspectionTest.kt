// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.LightServiceInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightService")
class LightServiceInspectionTest : LightServiceInspectionTestBase() {

  private val MAKE_FINAL_FIX_NAME = "Make 'MyService' final"
  private val ANNOTATE_AS_SERVICE_FIX_NAME = "Annotate class 'MyService' as '@Service'"
  private val CREATE_CONSTRUCTOR_FIX_NAME = "Create constructor in 'MyService'"
  private val REPLACE_WITH_GET_INSTANCE_FIX_NAME = "Replace with 'MyService.getInstance()' call"

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/lightService/"

  override fun getFileExtension() = "java"

  fun testMakeFinal() {
    doTest(MAKE_FINAL_FIX_NAME)
  }

  fun testMakeProjectLevel1() {
    doTest(ANNOTATE_AS_SERVICE_FIX_NAME)
  }

  fun testMakeProjectLevel2() {
    doTest(ANNOTATE_AS_SERVICE_FIX_NAME)
  }

  fun testCreateNoArgCtor() {
    doTest(CREATE_CONSTRUCTOR_FIX_NAME)
  }

  fun testReplaceWithGetInstanceApplicationLevel() {
    doTest(REPLACE_WITH_GET_INSTANCE_FIX_NAME)
  }

  fun testReplaceWithGetInstanceProjectLevel() {
    doTest(REPLACE_WITH_GET_INSTANCE_FIX_NAME)
  }

  fun testApplicationLevelServiceAsProjectLevel() {
    doTest()
  }

  fun testProjectLevelServiceAsApplicationLevel() {
    doTest()
  }
}
