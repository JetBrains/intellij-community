// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.quickfix.lightServiceMigrationFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightServiceMigration")
@TestApplication
internal class KtLightServiceMigrationInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val migration by lightServiceMigrationFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/lightServiceMigration")
  private val myFixture get() = migration.fixture

  private val CANNOT_BE_LIGHT_SERVICE_XML = "cannotBeLightService.xml"
  private val MY_SERVICE_KT = "MyService.kt"

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
    myFixture.copyFileToProject(MY_SERVICE_KT)
    migration.doTest(migration.convertToLightServiceAppLevel, migration.xmlExtension, myFixture.getTestName(true))
  }

  @Test
  fun testRemoveProjectServiceRegistration() {
    myFixture.copyFileToProject(MY_SERVICE_KT)
    migration.doTest(migration.convertToLightServiceProjectLevel, migration.xmlExtension, myFixture.getTestName(true))
  }

  @Test
  fun testOpenClass() {
    migration.doTest(migration.getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  @Test
  fun testServiceInterface() {
    migration.doTest(MY_SERVICE_KT, migration.getXmlFilePath())
  }

  @Test
  fun testPersistentStateComponent() {
    migration.doTest(migration.getCodeFilePath(), CANNOT_BE_LIGHT_SERVICE_XML)
  }

  @Test
  fun testPreload() {
    migration.doTest(MY_SERVICE_KT, migration.getXmlFilePath())
  }

  @Test
  fun testConfigurationSchemaKey() {
    migration.doTest(MY_SERVICE_KT, migration.getXmlFilePath())
  }

  @Test
  fun testLightService() {
    migration.doTest(migration.getCodeFilePath(), migration.getXmlFilePath())
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