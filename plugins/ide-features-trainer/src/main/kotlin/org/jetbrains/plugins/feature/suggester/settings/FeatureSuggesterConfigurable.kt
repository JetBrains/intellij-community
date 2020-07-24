package org.jetbrains.plugins.feature.suggester.settings

import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.Configurable
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import javax.swing.JComponent

class FeatureSuggesterConfigurable : Configurable, Configurable.WithEpDependencies {
    private val suggestingActionNames = FeatureSuggester.getSuggestingActionNames()
    private val settings = FeatureSuggesterSettings.instance()
    private val panel = FeatureSuggestersPanel(suggestingActionNames, settings)

    override fun isModified(): Boolean {
        if (settings.isAllEnabled() != panel.isAllSelected()) {
            return true
        }
        return suggestingActionNames.any { settings.isEnabled(it) != panel.isSelected(it) }
    }

    override fun apply() {
        settings.reset()
        if (!panel.isAllSelected()) {
            suggestingActionNames.forEach {
                if (!panel.isSelected(it)) {
                    settings.disableSuggester(it)
                }
            }
        }
    }

    override fun reset() {
        panel.loadFromSettings()
    }

    override fun createComponent(): JComponent? {
        return panel
    }

    override fun getDependencies(): MutableCollection<BaseExtensionPointName<*>> {
        return mutableListOf(FeatureSuggester.EP_NAME)
    }

    override fun getDisplayName(): String = "Feature Suggesters"
}