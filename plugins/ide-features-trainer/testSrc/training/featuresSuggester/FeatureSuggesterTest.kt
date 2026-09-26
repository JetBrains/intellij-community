// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private var disposable: Disposable? = null

  override fun setUp() {
    super.setUp()
    myFixture.configureByFile(testingCodeFileName)
    SuggestingUtils.forceShowSuggestions = true
    val settings = FeatureSuggesterSettings.instance()
    FeatureSuggester.suggesters.forEach { settings.setEnabled(it.id, true) }
    expectedSuggestion = NoSuggestion
    disposable = Disposer.newDisposable()
    FeatureSuggesterTestUtils.subscribeToSuggestions(myFixture.project, disposable!!) { suggestion -> expectedSuggestion = suggestion }
  }

  override fun tearDown() {
    try {
      disposable?.let { Disposer.dispose(it) }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun assertSuggestedCorrectly() {
    TestCase.assertTrue(expectedSuggestion.javaClass.name, expectedSuggestion is PopupSuggestion)
    TestCase.assertEquals(testingSuggesterId, (expectedSuggestion as PopupSuggestion).suggesterId)
  }
}
