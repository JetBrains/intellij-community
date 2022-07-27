package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.core.Language
import com.intellij.cce.dialog.configurable.FilterUIConfigurableFactory
import com.intellij.cce.dialog.configurable.UIConfigurable
import com.intellij.cce.filter.EvaluationFilterConfiguration
import com.intellij.cce.filter.EvaluationFilterManager
import com.intellij.cce.workspace.Config
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.EventDispatcher
import javax.swing.JPanel

class FiltersConfigurable(private val dispatcher: EventDispatcher<SettingsListener>, initLanguage: String) : EvaluationConfigurable {
  private val configurableMap: MutableMap<String, UIConfigurableInfo> = mutableMapOf()
  private var currentLanguage: String = initLanguage

  override fun createPanel(previousState: Config): JPanel {
    val panel = panel {
      group(EvaluationPluginBundle.message("evaluation.settings.filters.title")) {
        val provider = FilterUIConfigurableFactory(previousState, this)
        for (filter in EvaluationFilterManager.getAllFilters().filter { it.hasUI }) {
          getFilterView(filter, provider)
        }
      }
    }
    setFiltersByLanguage(currentLanguage)
    dispatcher.addListener(object : SettingsListener {
      override fun languageChanged(language: Language) = updateData(language.displayName)
    })
    return panel
  }

  private fun getFilterView(filter: EvaluationFilterConfiguration, provider: FilterUIConfigurableFactory): Row {
    val configurable = provider.build(filter.id)
    configurableMap[filter.id] = UIConfigurableInfo(configurable)
    return configurable.view
  }

  override fun configure(builder: Config.Builder) {
    for (entry in configurableMap) {
      if (entry.value.enabled)
        builder.filters[entry.key] = entry.value.configurable.build()
    }
  }

  private fun updateData(language: String) {
    currentLanguage = language
    if (language == Language.ANOTHER.displayName) {
      setFilterViewsEnabled(false)
    } else {
      setFilterViewsEnabled(true)
      setFiltersByLanguage(language)
    }
  }

  private fun setFiltersByLanguage(language: String) {
    configurableMap.forEach {
      if (!EvaluationFilterManager.getConfigurationById(it.key)!!.isLanguageSupported(language)) {
        it.value.enabled = false
      }
    }
  }

  private fun setFilterViewsEnabled(isEnabled: Boolean) = configurableMap.values.forEach { it.enabled = isEnabled }

}

private class UIConfigurableInfo(val configurable: UIConfigurable) {

  var enabled: Boolean = true
    set(value) {
      configurable.view.enabled(value)
      field = value
    }
}