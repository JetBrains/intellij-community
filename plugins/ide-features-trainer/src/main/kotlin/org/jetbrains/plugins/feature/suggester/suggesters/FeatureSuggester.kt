package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.Action

interface FeatureSuggester {
    companion object {
        val EP_NAME: ExtensionPointName<FeatureSuggester> =
            ExtensionPointName.create("org.intellij.featureSuggester.featureSuggester")

        val suggesters: List<FeatureSuggester>
            get() = EP_NAME.extensionList
    }

    /**
     * ID of languages for which this suggester can be applied
     */
    val languages: List<String>

    fun getSuggestion(action: Action): Suggestion

    fun isSuggestionNeeded(minNotificationIntervalDays: Int): Boolean

    val id: String

    val suggestingActionDisplayName: String
}
