package org.jetbrains.plugins.feature.suggester

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.actions.EditorFocusGainedAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggestersStatisticsCollector
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggestersStatisticsCollector.Companion.SUGGESTION_FOUND
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.ui.NotificationSuggestionPresenter
import org.jetbrains.plugins.feature.suggester.ui.SuggestionPresenter
import java.lang.ref.WeakReference

class FeatureSuggestersManager(val project: Project) : Disposable {
    companion object {
        private const val MAX_ACTIONS_NUMBER: Int = 100
    }

    private val actionsHistory = UserActionsHistory(MAX_ACTIONS_NUMBER)
    private val suggestionPresenter: SuggestionPresenter =
        NotificationSuggestionPresenter()
    private val settings = FeatureSuggesterSettings.instance()
    private val statisticsCollector = FeatureSuggestersStatisticsCollector()

    init {
        initFocusListener()
    }

    fun actionPerformed(action: Action) {
        val language = action.language ?: return
        val suggesters = FeatureSuggester.suggesters
            .filter { it.languages.find { id -> id == Language.ANY.id || id == language.id } != null }
        if (suggesters.isNotEmpty()) {
            actionsHistory.add(action)
            for (suggester in suggesters) {
                if (suggester.isEnabled()) {
                    processSuggester(suggester)
                }
            }
        }
    }

    private fun processSuggester(suggester: FeatureSuggester) {
        val suggestion = suggester.getSuggestion(actionsHistory)
        if (suggestion is PopupSuggestion) {
            statisticsCollector.sendStatistics(SUGGESTION_FOUND, suggester.id)
            if (suggester.isSuggestionNeeded(settings.suggestingIntervalDays)) {
                suggestionPresenter.showSuggestion(project, suggestion)
                fireSuggestionFound(suggestion)
            }
        }
    }

    private fun fireSuggestionFound(suggestion: PopupSuggestion) {
        project.messageBus.syncPublisher(FeatureSuggestersManagerListener.TOPIC)
            .featureFound(suggestion) // send event for testing
    }

    private fun initFocusListener() {
        val eventMulticaster = EditorFactory.getInstance().eventMulticaster as? EditorEventMulticasterEx
        eventMulticaster?.addFocusChangeListener(
            object : FocusChangeListener {
                override fun focusGained(editor: Editor) {
                    if (editor.project != project) return
                    actionPerformed(
                        EditorFocusGainedAction(
                            editorRef = WeakReference(editor),
                            timeMillis = System.currentTimeMillis()
                        )
                    )
                }
            },
            this
        )
    }

    override fun dispose() {
        actionsHistory.clear()
    }

    private fun FeatureSuggester.isEnabled(): Boolean {
        return FeatureSuggesterSettings.instance().isEnabled(suggestingActionDisplayName)
    }
}
