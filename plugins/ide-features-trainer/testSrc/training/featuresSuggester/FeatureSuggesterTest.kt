// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import training.featuresSuggester.settings.FeatureSuggesterSettings
import training.featuresSuggester.suggesters.FeatureSuggester

abstract class FeatureSuggesterTest : BasePlatformTestCase() {
  protected abstract val testingCodeFileName: String
  protected abstract val testingSuggesterId: String

  lateinit var expectedSuggestion: Suggestion
  private lateinit var disposable: Disposable

  override fun setUp() {
    super.setUp()
    myFixture.configureByFile(testingCodeFileName)
    SuggestingUtils.forceShowSuggestions = true
    val settings = FeatureSuggesterSettings.instance()
    FeatureSuggester.suggesters.forEach { settings.setEnabled(it.id, true) }
    expectedSuggestion = NoSuggestion
    disposable = Disposer.newDisposable()
    FeatureSuggesterTestUtils.subscribeToSuggestions(myFixture.project, disposable) { suggestion -> expectedSuggestion = suggestion }
  }

  override fun tearDown() {
    Disposer.dispose(disposable)
    super.tearDown()
  }

  fun assertSuggestedCorrectly() {
    TestCase.assertTrue(expectedSuggestion is PopupSuggestion)
    TestCase.assertEquals(testingSuggesterId, (expectedSuggestion as PopupSuggestion).suggesterId)
  }
}
