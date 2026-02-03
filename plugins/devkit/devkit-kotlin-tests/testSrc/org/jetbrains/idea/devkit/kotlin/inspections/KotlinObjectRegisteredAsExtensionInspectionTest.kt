// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/registrationProblems/code/extensions")
internal class KotlinObjectRegisteredAsExtensionInspectionTest : KotlinObjectExtensionRegistrationInspectionTestBase() {

  override val testedInspection = KotlinObjectRegisteredAsExtensionInspection()

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "/inspections/registrationProblems/code/extensions"

  fun testSingletonObjectsRegisteredAsExtension() {
    myFixture.testHighlighting("SingletonObjects.kt",
                               "singletonObjectExtensions.xml")
  }

  fun testInnerObjectsRegisteredAsExtension() {
    myFixture.testHighlighting("InnerObjects.kt",
                               "innerObjectExtensions.xml")
  }

  fun testCompanionObjectsRegisteredAsExtension() {
    myFixture.testHighlighting("CompanionObjects.kt",
                               "companionObjectExtensions.xml")
  }

  fun testCorrectClassesRegisteredAsExtension() {
    myFixture.testHighlighting("CorrectClassesAndObjects.kt",
                               "correctExtensions.xml")
  }

}
