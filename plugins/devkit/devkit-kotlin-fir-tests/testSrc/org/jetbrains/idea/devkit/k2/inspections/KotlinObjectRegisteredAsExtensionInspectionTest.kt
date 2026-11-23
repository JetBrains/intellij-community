// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.KotlinObjectRegisteredAsExtensionInspection
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/registrationProblems/code/extensions")
@TestApplication
internal class KotlinObjectRegisteredAsExtensionInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val pluginModule by kotlinObjectExtensionRegistrationFixture(projectFixture,
    DevkitKtTestsUtil.TESTDATA_PATH + "/inspections/registrationProblems/code/extensions") { KotlinObjectRegisteredAsExtensionInspection() }
  private val myFixture get() = pluginModule.fixture

  @Test
  fun testSingletonObjectsRegisteredAsExtension() {
    myFixture.testHighlighting("SingletonObjects.kt",
                               "singletonObjectExtensions.xml")
  }

  @Test
  fun testInnerObjectsRegisteredAsExtension() {
    myFixture.testHighlighting("InnerObjects.kt",
                               "innerObjectExtensions.xml")
  }

  @Test
  fun testCompanionObjectsRegisteredAsExtension() {
    myFixture.testHighlighting("CompanionObjects.kt",
                               "companionObjectExtensions.xml")
  }

  @Test
  fun testCorrectClassesRegisteredAsExtension() {
    myFixture.testHighlighting("CorrectClassesAndObjects.kt",
                               "correctExtensions.xml")
  }

}
