// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.annotations.PropertyKey

class EditorConfigIntentionTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/actions/intention/"

  fun testInvertBooleanValue() = doTest()
  fun testInvertSpaceValue() = doTest()
  fun testInvertUsualValue() {
    val testName = getTestName(true)
    myFixture.configureByFile("$testName/.editorconfig")
    assertIntentionUnavailable()
  }

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("$testName/.editorconfig")
    val quickFix = findInvertOptionValueIntention()
    myFixture.launchAction(quickFix)
    myFixture.checkResultByFile("$testName/result.txt")
  }

  private fun findInvertOptionValueIntention(): IntentionAction {
    val availableIntentions = myFixture.availableIntentions
    val intentionName = invertOptionValueIntentionName()
    val result = availableIntentions.firstOrNull { it.text == intentionName }
    return result ?: throw AssertionError("Intention '$intentionName' not found among ${availableIntentions.map(IntentionAction::getText)}")
  }

  private fun assertIntentionUnavailable() {
    val availableIntentions = myFixture.availableIntentions
    val intentionName = invertOptionValueIntentionName()
    assertTrue(
      "Intention '$intentionName' should not be available among ${availableIntentions.map(IntentionAction::getText)}",
      availableIntentions.none { it.text == intentionName },
    )
  }

  private fun invertOptionValueIntentionName() = EditorConfigBundle[INVERT_OPTION_VALUE_KEY]

  private companion object {
    @PropertyKey(resourceBundle = EditorConfigBundle.BUNDLE)
    private const val INVERT_OPTION_VALUE_KEY = "intention.invert-option-value"
  }
}
