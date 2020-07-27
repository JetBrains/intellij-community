package org.jetbrains.plugins.feature.suggester

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import org.jetbrains.plugins.feature.suggester.ui.NotificationSuggestionPresenter
import org.jetbrains.plugins.feature.suggester.ui.SuggestionPresenter

class FeatureSuggestersManager(val project: Project) {
    private val MAX_ACTIONS_NUMBER: Int = 100
    private val actionsHistory = UserActionsHistory(MAX_ACTIONS_NUMBER)
    private val suggestionPresenter: SuggestionPresenter =
        NotificationSuggestionPresenter()

    fun actionPerformed(action: Action) {
        val language = action.language ?: return
        val langSupport = LanguageSupport.getForLanguage(language)
        if(langSupport != null) {
            actionsHistory.add(action)
            processSuggesters(langSupport)
        }
    }

    private fun processSuggesters(langSupport: LanguageSupport) {
        for (suggester in FeatureSuggester.suggesters) {
            if (suggester.isEnabled() && suggester.isSuggestionNeeded()) {
                suggester.langSupport = langSupportprocessSuggester(suggester)
            }
        }
    }

    private fun processSuggester(suggester: FeatureSuggester) {
        val suggestion = suggester.getSuggestion(actionsHistory)
        if (suggestion is PopupSuggestion) {
            if (suggester.needToClearLookup) {
                //todo: this is hack to avoid exception in spection completion case
                val lookupManager = LookupManager.getInstance(project)
                lookupManager as LookupManagerImpl
                lookupManager.clearLookup()
            }
            suggestionPresenter.showSuggestion(project, suggestion)

            // send event for testing
            project.messageBus.syncPublisher(FeatureSuggestersManagerListener.TOPIC).featureFound(suggestion)
        }
    }

    private fun FeatureSuggester.isEnabled(): Boolean {
        return FeatureSuggesterSettings.instance().isEnabled(suggestingActionDisplayName)
    }
}