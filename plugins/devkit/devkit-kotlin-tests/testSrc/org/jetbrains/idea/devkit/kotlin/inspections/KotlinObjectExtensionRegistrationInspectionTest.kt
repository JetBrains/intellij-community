// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/registrationProblems/xml/extensions/")
internal class KotlinObjectExtensionRegistrationInspectionTest : KotlinObjectExtensionRegistrationInspectionTestBase() {

  override val testedInspection = KotlinObjectExtensionRegistrationInspection()

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml/extensions"

  fun testSingletonObjectExtensionsRegistered() {
    myFixture.testHighlighting("singletonObjectExtensions.xml",
                               "Configurables.kt",
                               "FileTypes.kt",
                               "IntentionActions.kt",
                               "Services.kt")
  }

  fun testInnerObjectExtensionsRegistered() {
    myFixture.testHighlighting("innerObjectExtensions.xml",
                               "InnerConfigurables.kt",
                               "InnerFileTypes.kt",
                               "InnerIntentionActions.kt",
                               "InnerServices.kt")
  }

  fun testCompanionObjectExtensionsRegistered() {
    myFixture.testHighlighting("companionObjectExtensions.xml",
                               "CompanionObjectConfigurables.kt",
                               "CompanionObjectFileTypes.kt",
                               "CompanionObjectIntentionActions.kt",
                               "CompanionObjectServices.kt")
  }

}
