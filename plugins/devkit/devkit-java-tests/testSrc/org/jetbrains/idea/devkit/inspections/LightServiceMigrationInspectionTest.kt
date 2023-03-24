// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightServiceMigration")
internal class LightServiceMigrationInspectionTest : LightServiceMigrationInspectionTestBase() {

  private val CANNOT_BE_LIGHT_SERVICE_XML = "cannotBeLightService.xml"
  private val CANNOT_BE_LIGHT_SERVICE_JAVA = "CannotBeLightService.java"

  override fun getBasePath(): String = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/lightServiceMigration"

  fun testCanBeLightServiceAppLevel() {
    myFixture.testHighlightingAllFiles(true, false, false,
                                       getTestName(false) + ".java",
                                       getTestName(true) + ".xml")
  }

  fun testCanBeLightServiceProjectLevel() {
    myFixture.testHighlightingAllFiles(true, false, false,
                                       getTestName(false) + ".java",
                                       getTestName(true) + ".xml")
  }

  fun testNonFinalClass() {
    myFixture.testHighlightingAllFiles(true, false, false,
                                       getTestName(false) + ".java",
                                       CANNOT_BE_LIGHT_SERVICE_XML)
  }

  fun testServiceInterface() {
    myFixture.testHighlightingAllFiles(true, false, false,
                                       CANNOT_BE_LIGHT_SERVICE_JAVA,
                                       getTestName(true) + ".xml")
  }

  fun testPersistentStateComponent() {
    myFixture.testHighlightingAllFiles(true, false, false,
                                       getTestName(false) + ".java",
                                       CANNOT_BE_LIGHT_SERVICE_XML)
  }

  fun testPreload() {
    myFixture.testHighlightingAllFiles(true, false, false,
                                       CANNOT_BE_LIGHT_SERVICE_JAVA,
                                       getTestName(true) + ".xml")
  }
}