// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.featuresSuggester

import com.intellij.testFramework.TestIndexingModeSupporter
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit

abstract class ReplaceCompletionSuggesterTest : FeatureSuggesterTest(), TestIndexingModeSupporter {
  private var indexingModeValue = IndexingMode.SMART
  override val testingSuggesterId = "Completion with replace"

  abstract fun `testDelete and type dot, complete method call, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, complete method call, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, complete with property, remove previous identifier and get suggestion`()

  abstract fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, type additional characters, complete, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, complete method call, remove another equal identifier and don't get suggestion`()

  protected fun CodeInsightTestFixture.deleteAndTypeDot() {
    deleteSymbolAtCaret()
    typeAndCommit(".")
  }

  override fun setIndexingMode(mode: IndexingMode) {
    indexingModeValue = mode
  }

  override fun getIndexingMode(): IndexingMode = indexingModeValue

  override fun setUp() {
    super.setUp()
    indexingModeValue.setUpTest(myFixture.project, myFixture.testRootDisposable)
  }

  override fun tearDown() {
    try {
      indexingModeValue.tearDownTest(myFixture.project)
    }
    catch (e: Exception) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}
