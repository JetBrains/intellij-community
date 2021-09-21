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
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggesterStatistics
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggesterStatistics.Companion.SUGGESTION_FOUND
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.ui.NotificationSuggestionPresenter
import org.jetbrains.plugins.feature.suggester.ui.SuggestionPresenter

class FeatureSuggestersManager(val project: Project) : Disposable {
    private val suggestionPresenter: SuggestionPresenter =
        NotificationSuggestionPresenter()

    init {
        if (!project.isDefault) initFocusListener()
    }

    fun actionPerformed(action: Action) {
        if (project.isDisposed) return
        val language = action.language ?: return
        val suggesters = FeatureSuggester.suggesters
            .filter { it.languages.find { id -> id == Language.ANY.id || id == language.id } != null }
        if (suggesters.isNotEmpty()) {
            for (suggester in suggesters) {
                if (suggester.isEnabled()) {
                    processSuggester(suggester, action)
                }
            }
        }
    }

    private fun processSuggester(suggester: FeatureSuggester, action: Action) {
        val suggestion = suggester.getSuggestion(action)
        if (suggestion is PopupSuggestion) {
            FeatureSuggesterStatistics.sendStatistics(SUGGESTION_FOUND, suggester.id)
            if (suggester.isSuggestionNeeded(FeatureSuggesterSettings.instance().suggestingIntervalDays)) {
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
                            editor = editor,
                            timeMillis = System.currentTimeMillis()
                        )
                    )
                }
            },
            this
        )
    }

    override fun dispose() {}

    private fun FeatureSuggester.isEnabled(): Boolean {
        return FeatureSuggesterSettings.instance().isEnabled(id)
    }
}
