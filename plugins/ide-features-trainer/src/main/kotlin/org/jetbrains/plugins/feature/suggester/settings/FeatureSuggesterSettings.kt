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

    fun isEnabled(id: String): Boolean {
        for (s in state.disabledSuggesters) {
            if (s == id)
                return false
        }
        return true
    }

    fun disableSuggester(id: String) {
        state.disabledSuggesters.add(id)
    }

    fun enableSuggester(id: String) {
        state.disabledSuggesters.remove(id)
    }
}