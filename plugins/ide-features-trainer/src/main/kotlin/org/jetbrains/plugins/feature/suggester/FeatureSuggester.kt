package org.jetbrains.plugins.feature.suggester

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory

interface FeatureSuggester {

    companion object {
        val EP_NAME: ExtensionPointName<FeatureSuggester> =
            ExtensionPointName.create("org.intellij.featureSuggester.featureSuggester")

        val suggesters: List<FeatureSuggester> = EP_NAME.extensionList
    }

    val needToClearLookup: Boolean
        get() = false

    fun getSuggestion(actions: UserActionsHistory): Suggestion

    fun getId(): String
}