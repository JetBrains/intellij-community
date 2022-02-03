package training.featuresSuggester.suggesters

import com.intellij.openapi.extensions.ExtensionPointName
import training.featuresSuggester.Suggestion
import training.featuresSuggester.actions.Action

interface FeatureSuggester {
  companion object {
    val EP_NAME: ExtensionPointName<FeatureSuggester> =
      ExtensionPointName.create("training.ifs.suggester")

    val suggesters: List<FeatureSuggester>
      get() = EP_NAME.extensionList
  }

  /**
   * ID of languages for which this suggester can be applied
   */
  val languages: List<String>

  fun getSuggestion(action: Action): Suggestion

  fun isSuggestionNeeded(): Boolean

  val id: String

  val suggestingActionDisplayName: String

  val minSuggestingIntervalDays: Int
}
