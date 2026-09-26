package training.featuresSuggester.settings

import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.ThreeStateCheckBox
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.suggesters.FeatureSuggester

class FeatureSuggesterConfigurable : BoundConfigurable(
  FeatureSuggesterBundle.message("configurable.name")), Configurable.WithEpDependencies {

  private lateinit var toggleAllCheckBox: ThreeStateCheckBox
  private val checkBoxes = mutableListOf<JBCheckBox>()

  override fun createPanel(): DialogPanel {
    val settings = FeatureSuggesterSettings.instance()

    return panel {
      row {
        text(FeatureSuggesterBundle.message("configurable.explanation"))
      }
      row {
        toggleAllCheckBox = threeStateCheckBox(FeatureSuggesterBundle.message("configurable.show.suggestions.checkbox"))
          .applyToComponent {
            isThirdStateEnabled = false
          }.onChanged {
            if (it.state != ThreeStateCheckBox.State.DONT_CARE) {
              val selected = it.isSelected
              for (checkBox in checkBoxes) {
                checkBox.isSelected = selected
              }
            }
          }.component
      }

      indent {
        for (suggester in FeatureSuggester.suggesters) {
          val id = suggester.id
          row {
            val checkBox = checkBox(suggester.suggestingActionDisplayName)
              .bindSelected({ settings.isEnabled(id) }, { settings.setEnabled(id, it) })
              .onChanged { updateToggleAllCheckBox() }
              .component
            checkBoxes.add(checkBox)
          }
        }
      }
    }
  }

  override fun reset() {
    super.reset()
    updateToggleAllCheckBox()
  }

  override fun getDependencies(): MutableCollection<BaseExtensionPointName<*>> {
    return mutableListOf(FeatureSuggester.EP_NAME)
  }

  private fun updateToggleAllCheckBox() {
    val selectedCount = checkBoxes.filter { it.isSelected }.size
    toggleAllCheckBox.state = when (selectedCount) {
      0 -> ThreeStateCheckBox.State.NOT_SELECTED
      checkBoxes.size -> ThreeStateCheckBox.State.SELECTED
      else -> ThreeStateCheckBox.State.DONT_CARE
    }
  }
}
