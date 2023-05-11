// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import kotlin.io.path.*

@TestDataPath("\$CONTENT_ROOT/testData/inspections/applicationServiceAsStaticFinalField")
class ApplicationServiceAsStaticFinalFieldInspectionTest : ApplicationServiceAsStaticFinalFieldInspectionTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/applicationServiceAsStaticFinalField"

  override val fileType: String
    get() = "java"

  fun testNonServicesInFields() {
    doTest()
  }

  fun testRegisteredServicesInFields() {
    doTest()
 }

  fun testExplicitConstructorCallInFields() {
    doTest()
  }

  fun testLightServicesInFields() {
    doTest()
  }

}
