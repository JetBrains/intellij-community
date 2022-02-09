// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit

abstract class ReplaceCompletionSuggesterTest : FeatureSuggesterTest() {
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
}
