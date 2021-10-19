package training.featuresSuggester.replaceCompletion

import training.featuresSuggester.FeatureSuggesterTest

abstract class ReplaceCompletionSuggesterTest : FeatureSuggesterTest() {
  override val testingSuggesterId = "Completion with replace"

  abstract fun `testDelete and type dot, complete method call, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, complete method call, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, complete with property, remove previous identifier and get suggestion`()

  abstract fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, type additional characters, complete, remove previous identifier and get suggestion`()

  abstract fun `testCall completion, complete method call, remove another equal identifier and don't get suggestion`()

  protected fun deleteAndTypeDot() {
    deleteSymbolAtCaret()
    type(".")
  }
}
