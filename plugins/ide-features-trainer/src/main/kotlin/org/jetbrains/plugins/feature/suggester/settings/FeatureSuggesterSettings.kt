package org.jetbrains.plugins.feature.suggester.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.XCollection


@State(
    name = "FeatureSuggesterSettings",
    storages = [Storage("FeatureSuggester.xml")]
)
class FeatureSuggesterSettings : SimplePersistentStateComponent<FeatureSuggesterSettings.State>(State()) {
    companion object {
        @JvmStatic
        fun instance(): FeatureSuggesterSettings {
            return ApplicationManager.getApplication().getService(FeatureSuggesterSettings::class.java)
        }
    }

    class State : BaseState() {
        @get:XCollection
        val disabledSuggesters by list<String>()
    }

    fun reset() {
        state.disabledSuggesters.clear()
    }

    fun isEnabled(suggestingActionDisplayName: String): Boolean {
        return !state.disabledSuggesters.contains(suggestingActionDisplayName)
    }

    fun isAllEnabled(): Boolean {
        return state.disabledSuggesters.isEmpty()
    }

    fun disableSuggester(suggestingActionDisplayName: String) {
        state.disabledSuggesters.add(suggestingActionDisplayName)
    }

    fun enableSuggester(suggestingActionDisplayName: String) {
        state.disabledSuggesters.remove(suggestingActionDisplayName)
    }
}