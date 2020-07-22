package org.jetbrains.plugins.feature.suggester

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.actions.listeners.DocumentActionsListener
import org.jetbrains.plugins.feature.suggester.actions.listeners.EditorActionsListener
import org.jetbrains.plugins.feature.suggester.actions.listeners.PsiActionsListener
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings

class FeatureSuggestersManager(val project: Project) {
    private val MAX_ACTIONS_NUMBER: Int = 100
    private val actionsHistory = UserActionsHistory(MAX_ACTIONS_NUMBER)
    private val suggestionPresenter: SuggestionPresenter = NotificationSuggestionPresenter()

    fun actionPerformed(action: Action) {
        actionsHistory.add(action)
        processSuggesters()
    }

    private fun processSuggesters() {
        for (suggester in FeatureSuggester.suggesters) {
            if (!suggester.isEnabled()) continue
            processSuggester(suggester)
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
            suggestionPresenter.showSuggestion(project, suggestion.message)

            // send event for testing
            project.messageBus.syncPublisher(FeatureSuggestersManagerListener.TOPIC).featureFound(suggestion)
        }
    }

    private fun FeatureSuggester.isEnabled(): Boolean {
        return FeatureSuggesterSettings.isEnabled(getId())
    }
}