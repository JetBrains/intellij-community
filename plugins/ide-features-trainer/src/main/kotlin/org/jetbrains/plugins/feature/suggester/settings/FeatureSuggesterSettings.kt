package org.jetbrains.plugins.feature.suggester.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil


@State(
    name = "FeatureSuggesterSettings",
    storages = [Storage("FeatureSuggester.xml")]
)
object FeatureSuggesterSettings : PersistentStateComponent<FeatureSuggesterSettings> {
    private val disabledSuggesters: List<String> = ArrayList()

    fun isEnabled(id: String): Boolean {
        for (s in disabledSuggesters) {
            if (s == id)
                return false
        }
        return true
    }

    override fun getState(): FeatureSuggesterSettings {
        return this
    }

    override fun loadState(state: FeatureSuggesterSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as FeatureSuggesterSettings
        return disabledSuggesters == other.disabledSuggesters
    }
}