// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import org.jetbrains.idea.devkit.inspections.InspectionDescriptionNotFoundInspection
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import java.nio.file.Paths

@TestDataPath("\$CONTENT_ROOT/testData/inspections/inspectionDescription")
class KtInspectionDescriptionNotFoundInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun getBasePath(): String? {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/inspectionDescription"
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("core-api", PathUtil.getJarPathForClass(LanguageExtensionPoint::class.java))
    moduleBuilder.addLibrary("analysis-api", PathUtil.getJarPathForClass(LocalInspectionEP::class.java))
    moduleBuilder.addLibrary("platform-resources",
                             Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP::class.java))
                               .resolveSibling("intellij.platform.resources").toString())
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(InspectionDescriptionNotFoundInspection::class.java)
  }

  fun testHighlightingForDescription() {
    myFixture.testHighlighting("MyInspection.kt")
  }

  fun testOverridePathMethod() {
    myFixture.testHighlighting("MyOverridePathMethodInspection.kt")
  }

  fun testHighlightingForDescriptionCustomShortName() {
    myFixture.testHighlighting("MyInspectionCustomShortName.kt");
  }

  fun testWithDescription() {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions")
    myFixture.testHighlighting("MyWithDescriptionInspection.kt")
  }

  fun testWithDescriptionCustomShortName() {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions")
    myFixture.testHighlighting("MyWithDescriptionCustomShortNameInspection.kt")
  }

}