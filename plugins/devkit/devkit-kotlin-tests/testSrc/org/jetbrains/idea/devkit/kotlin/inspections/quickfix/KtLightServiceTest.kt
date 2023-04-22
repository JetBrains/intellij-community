// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.LightServiceTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightService")
class KtLightServiceTest : LightServiceTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/lightService/"

  override fun getFileExtension() = "kt"

  fun testMakeNotOpen() {
    doTest("Make 'MyService' not open")
  }

  fun testMakeProjectLevel1() {
    doTest("Annotate as @Service")
  }

  fun testMakeProjectLevel2() {
    doTest("Annotate as @Service")
  }

  fun testCreateNoArgCtor() {
    doTest("Remove 1st parameter from constructor 'MyService'")
  }

  fun testCreateCoroutineScopeCtor() {
    doTest("Change 1st parameter of constructor 'MyService' from 'Project' to 'CoroutineScope'")
  }
}

