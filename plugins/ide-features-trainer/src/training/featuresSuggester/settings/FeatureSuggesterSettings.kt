package training.featuresSuggester.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import training.featuresSuggester.suggesters.FeatureSuggester

@State(
  name = "FeatureSuggesterSettings",
  storages = [Storage("FeatureSuggester.xml")]
)
class FeatureSuggesterSettings : PersistentStateComponent<FeatureSuggesterSettings> {
  var suggestingIntervalDays: Int = DEFAULT_SUGGESTING_INTERVAL_DAYS
  var suggesters: MutableMap<String, Boolean> = FeatureSuggester.suggesters.associate { it.id to true }.toMutableMap()

  override fun getState(): FeatureSuggesterSettings {
    return this
  }

  override fun loadState(state: FeatureSuggesterSettings) {
    suggestingIntervalDays = state.suggestingIntervalDays
    suggesters = state.suggesters
  }

  fun isEnabled(suggesterId: String): Boolean {
    return suggesters[suggesterId] == true
  }

  fun setEnabled(suggesterId: String, enabled: Boolean) {
    suggesters[suggesterId] = enabled
  }

  companion object {
    @JvmStatic
    fun instance(): FeatureSuggesterSettings {
      return ApplicationManager.getApplication().getService(FeatureSuggesterSettings::class.java)
    }

    const val DEFAULT_SUGGESTING_INTERVAL_DAYS = 14
  }
}
