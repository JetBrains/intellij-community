// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.quickfix.LightServiceMigrationInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightServiceMigration")
internal class KtLightServiceMigrationInspectionTest : LightServiceMigrationInspectionTestBase() {

  private val CANNOT_BE_LIGHT_SERVICE_XML = "cannotBeLightService.xml"
  private val CANNOT_BE_LIGHT_SERVICE_KT = "CannotBeLightService.kt"
  private val MY_SERVICE_KT = "MyService.kt"

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/lightServiceMigration"
  override fun getFileExtension(): String = "kt"

  fun testAddAppServiceAnnotation() {
    myFixture.copyFileToProject(getXmlFilePath())
    doTest(convertToLightServiceAppLevel, fileExtension, getTestName(false))
  }

  fun testAddProjectServiceAnnotation() {
    myFixture.copyFileToProject(getXmlFilePath())
    doTest(convertToLightServiceProjectLevel, fileExtension, getTestName(false))
  }

  fun testRemoveAppServiceRegistration() {
    myFixture.copyFileToProject(MY_SERVICE_KT)
    doTest(convertToLightServiceAppLevel, xmlExtension, getTestName(true))
  }

  fun testRemoveProjectServiceRegistration() {
    myFixture.copyFileToProject(MY_SERVICE_KT)
    doTest(convertToLightServiceProjectLevel, xmlExtension, getTestName(true))
  }

  fun testOpenClass() {
    doTest(getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  fun testServiceInterface() {
    doTest(CANNOT_BE_LIGHT_SERVICE_KT, getXmlFilePath())
  }

  fun testPersistentStateComponent() {
    doTest(getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  fun testPreload() {
    doTest(CANNOT_BE_LIGHT_SERVICE_KT, getXmlFilePath())
  }

  fun testLightService() {
    doTest(getCodeFilePath(), getXmlFilePath())
  }

  fun testUnitTestMode() {
    doTest(getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  fun testHeadlessEnvironment() {
    doTest(getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }
}