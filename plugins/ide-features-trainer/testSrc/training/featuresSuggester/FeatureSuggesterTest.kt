// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import training.featuresSuggester.settings.FeatureSuggesterSettings

abstract class FeatureSuggesterTest : BasePlatformTestCase() {
  protected abstract val testingCodeFileName: String
  protected abstract val testingSuggesterId: String

  lateinit var expectedSuggestion: Suggestion

  override fun setUp() {
    super.setUp()
    myFixture.configureByFile(testingCodeFileName)
    FeatureSuggesterSettings.instance().suggestingIntervalDays = 0
    expectedSuggestion = NoSuggestion
    FeatureSuggesterTestUtils.subscribeToSuggestions(myFixture.project) { suggestion -> expectedSuggestion = suggestion }
  }

  fun assertSuggestedCorrectly() {
    TestCase.assertTrue(expectedSuggestion is PopupSuggestion)
    TestCase.assertEquals(testingSuggesterId, (expectedSuggestion as PopupSuggestion).suggesterId)
  }
}
