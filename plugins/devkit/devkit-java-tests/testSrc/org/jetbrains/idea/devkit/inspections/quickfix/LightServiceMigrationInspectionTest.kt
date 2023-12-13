// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightServiceMigration")
internal class LightServiceMigrationInspectionTest : LightServiceMigrationInspectionTestBase() {

  private val CANNOT_BE_LIGHT_SERVICE_XML = "cannotBeLightService.xml"
  private val CANNOT_BE_LIGHT_SERVICE_JAVA = "CannotBeLightService.java"
  private val MY_SERVICE_JAVA = "MyService.java"

  override fun getBasePath(): String = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/lightServiceMigration"
  override fun getFileExtension(): String = "java"

  fun testAddAppServiceAnnotation() {
    myFixture.copyFileToProject(getXmlFilePath())
    doTest(convertToLightServiceAppLevel, fileExtension, getTestName(false))
  }

  fun testAddProjectServiceAnnotation() {
    myFixture.copyFileToProject(getXmlFilePath())
    doTest(convertToLightServiceProjectLevel, fileExtension, getTestName(false))
  }

  fun testRemoveAppServiceRegistration() {
    myFixture.copyFileToProject(MY_SERVICE_JAVA)
    doTest(convertToLightServiceAppLevel, xmlExtension, getTestName(true))
  }

  fun testRemoveProjectServiceRegistration() {
    myFixture.copyFileToProject(MY_SERVICE_JAVA)
    doTest(convertToLightServiceProjectLevel, xmlExtension, getTestName(true))
  }

  fun testNonFinalClass() {
    doTest(getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  fun testServiceInterface() {
    doTest(CANNOT_BE_LIGHT_SERVICE_JAVA, getXmlFilePath())
  }

  fun testPersistentStateComponent() {
    doTest(getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  fun testPreload() {
    doTest(CANNOT_BE_LIGHT_SERVICE_JAVA, getXmlFilePath())
  }

  fun testLightService() {
    myFixture.copyFileToProject(getCodeFilePath())
    doTest("Remove <applicationService> element", xmlExtension, getTestName(true))
  }

  fun testLibraryClass() {
    myFixture.testHighlighting(getTestName(true) + "." + xmlExtension)
  }

  fun testUnitTestMode() {
    doTest(getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  fun testHeadlessEnvironment() {
    doTest(getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }
}