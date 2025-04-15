// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.editorconfig.language.messages.EditorConfigBundle
import org.jetbrains.annotations.PropertyKey

class EditorConfigIntentionTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/actions/intention/"

  fun testInvertBooleanValue() = doTest("intention.invert-option-value")
  fun testInvertSpaceValue() = doTest("intention.invert-option-value")
  fun testInvertUsualValue() {
    val testName = getTestName(true)
    myFixture.configureByFile("$testName/.editorconfig")
    assertNoIntentions()
  }

  private fun doTest(@PropertyKey(resourceBundle = EditorConfigBundle.BUNDLE) intentionKey: String) {
    val testName = getTestName(true)
    myFixture.configureByFile("$testName/.editorconfig")
    val quickFix = findIntention(intentionKey)
    myFixture.launchAction(quickFix)
    myFixture.checkResultByFile("$testName/result.txt")
  }

  private fun findIntention(@PropertyKey(resourceBundle = EditorConfigBundle.BUNDLE) intentionKey: String): IntentionAction {
    val availableIntentions = myFixture.availableIntentions
    val intentionName = EditorConfigBundle[intentionKey]
    val result = availableIntentions.firstOrNull { it.text == intentionName }
    return result ?: throw AssertionError("Intention '$intentionName' not found among ${availableIntentions.map(IntentionAction::getText)}")
  }

  private fun assertNoIntentions() = assertEquals(0, myFixture.availableIntentions.size)
}
