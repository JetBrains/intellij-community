// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightServiceMigration")
@TestApplication
internal class LightServiceMigrationInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val migration by lightServiceMigrationFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/lightServiceMigration")
  private val myFixture get() = migration.fixture

  private val CANNOT_BE_LIGHT_SERVICE_XML = "cannotBeLightService.xml"
  private val MY_SERVICE_JAVA = "MyService.java"

  @Test
  fun testAddAppServiceAnnotation() {
    myFixture.copyFileToProject(migration.getXmlFilePath())
    migration.doTest(migration.convertToLightServiceAppLevel, migration.fileExtension, myFixture.getTestName(false))
  }

  @Test
  fun testAddProjectServiceAnnotation() {
    myFixture.copyFileToProject(migration.getXmlFilePath())
    migration.doTest(migration.convertToLightServiceProjectLevel, migration.fileExtension, myFixture.getTestName(false))
  }

  @Test
  fun testRemoveAppServiceRegistration() {
    myFixture.copyFileToProject(MY_SERVICE_JAVA)
    migration.doTest(migration.convertToLightServiceAppLevel, migration.xmlExtension, myFixture.getTestName(true))
  }

  @Test
  fun testRemoveProjectServiceRegistration() {
    myFixture.copyFileToProject(MY_SERVICE_JAVA)
    migration.doTest(migration.convertToLightServiceProjectLevel, migration.xmlExtension, myFixture.getTestName(true))
  }

  @Test
  fun testNonFinalClass() {
    migration.doTest(migration.getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  @Test
  fun testServiceInterface() {
    migration.doTest(MY_SERVICE_JAVA, migration.getXmlFilePath())
  }

  @Test
  fun testPersistentStateComponent() {
    migration.doTest(migration.getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  @Test
  fun testPreload() {
    migration.doTest(MY_SERVICE_JAVA, migration.getXmlFilePath())
  }

  @Test
  fun testConfigurationSchemaKey() {
    migration.doTest(MY_SERVICE_JAVA, migration.getXmlFilePath())
  }

  @Test
  fun testLightService() {
    myFixture.copyFileToProject(migration.getCodeFilePath())
    migration.doTest("Remove <applicationService> element", migration.xmlExtension, myFixture.getTestName(true))
  }

  @Test
  fun testLibraryClass() {
    myFixture.testHighlighting(myFixture.getTestName(true) + "." + migration.xmlExtension)
  }

  @Test
  fun testUnitTestMode() {
    migration.doTest(migration.getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  @Test
  fun testHeadlessEnvironment() {
    migration.doTest(migration.getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }
}