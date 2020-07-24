package org.jetbrains.plugins.feature.suggester.settings

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

    class State : BaseState() {
        @get:XCollection
        val disabledSuggesters by list<String>()
    }

    fun isEnabled(suggestingActionDisplayName: String): Boolean {
        for (s in state.disabledSuggesters) {
            if (s == suggestingActionDisplayName)
                return false
        }
        return true
    }

    fun disableSuggester(suggestingActionDisplayName: String) {
        state.disabledSuggesters.add(suggestingActionDisplayName)
    }

    fun enableSuggester(suggestingActionDisplayName: String) {
        state.disabledSuggesters.remove(suggestingActionDisplayName)
    }
}