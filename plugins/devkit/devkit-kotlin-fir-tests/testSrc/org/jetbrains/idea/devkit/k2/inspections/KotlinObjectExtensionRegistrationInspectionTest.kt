// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.KotlinObjectExtensionRegistrationInspection
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/registrationProblems/xml/extensions/")
@TestApplication
internal class KotlinObjectExtensionRegistrationInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val pluginModule by kotlinObjectExtensionRegistrationFixture(projectFixture,
    DevkitKtTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml/extensions") { KotlinObjectExtensionRegistrationInspection() }
  private val myFixture get() = pluginModule.fixture

  @Test
  fun testSingletonObjectExtensionsRegistered() {
    myFixture.testHighlighting("singletonObjectExtensions.xml",
                               "Configurables.kt",
                               "FileTypes.kt",
                               "IntentionActions.kt",
                               "Services.kt")
  }

  @Test
  fun testInnerObjectExtensionsRegistered() {
    myFixture.testHighlighting("innerObjectExtensions.xml",
                               "InnerConfigurables.kt",
                               "InnerFileTypes.kt",
                               "InnerIntentionActions.kt",
                               "InnerServices.kt")
  }

  @Test
  fun testCompanionObjectExtensionsRegistered() {
    myFixture.testHighlighting("companionObjectExtensions.xml",
                               "CompanionObjectConfigurables.kt",
                               "CompanionObjectFileTypes.kt",
                               "CompanionObjectIntentionActions.kt",
                               "CompanionObjectServices.kt")
  }

}
