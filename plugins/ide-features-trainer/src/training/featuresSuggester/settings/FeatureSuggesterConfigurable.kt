package training.featuresSuggester.settings

import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.Configurable
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.suggesters.FeatureSuggester
import javax.swing.JComponent

class FeatureSuggesterConfigurable : Configurable, Configurable.WithEpDependencies {
  private val suggesterIdToName = FeatureSuggester.suggesters.associate { it.id to it.suggestingActionDisplayName }
  private val settings = FeatureSuggesterSettings.instance()
  private val panel = FeatureSuggestersPanel(suggesterIdToName, settings)

  override fun isModified(): Boolean {
    return suggesterIdToName.keys.any { settings.isEnabled(it) != panel.isSelected(it) }
  }

  override fun apply() {
    suggesterIdToName.keys.forEach { suggesterId ->
      settings.setEnabled(suggesterId, panel.isSelected(suggesterId))
    }
  }

  override fun reset() {
    panel.loadFromSettings()
  }

  override fun createComponent(): JComponent {
    return panel
  }

  override fun getDependencies(): MutableCollection<BaseExtensionPointName<*>> {
    return mutableListOf(FeatureSuggester.EP_NAME)
  }

  override fun getDisplayName(): String = FeatureSuggesterBundle.message("configurable.name")
}
