package org.jetbrains.plugins.feature.suggester

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.actions.EditorFocusGainedAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
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

    init {
        initFocusListener()
    }

    fun actionPerformed(action: Action) {
        val language = action.language ?: return
        val langSupport = getLanguageSupport(language)
        if (langSupport != null) {
            actionsHistory.add(action)
            processSuggesters(langSupport)
        }
    }

    private fun getLanguageSupport(language: Language): LanguageSupport? {
        val langSupport = LanguageSupport.getForLanguage(language)
        return if (langSupport == null && language.baseLanguage != null) {
            LanguageSupport.getForLanguage(language.baseLanguage!!)
        } else {
            langSupport
        }
    }

    private fun processSuggesters(langSupport: LanguageSupport) {
        for (suggester in FeatureSuggester.suggesters) {
            if (suggester.isEnabled() && suggester.isSuggestionNeeded(settings.suggestingIntervalDays)) {
                suggester.langSupport = langSupportprocessSuggester(suggester)
            }
        }
    }

    private fun processSuggester(suggester: FeatureSuggester) {
        val suggestion = suggester.getSuggestion(actionsHistory)
        if (suggestion is PopupSuggestion) {
            suggestionPresenter.showSuggestion(project, suggestion)
            fireSuggestionFound(suggestion)
        }
    }

    private fun fireSuggestionFound(suggestion: PopupSuggestion) {
        project.messageBus.syncPublisher(FeatureSuggestersManagerListener.TOPIC).featureFound(suggestion) // send event for testing
    }

    private fun initFocusListener() {
        val eventMulticaster = EditorFactory.getInstance().eventMulticaster as? EditorEventMulticasterEx
        eventMulticaster?.addFocusChangeListener(FocusChangeListener { editor ->
            if (editor.project != project) return@FocusChangeListener
            actionPerformed(
                EditorFocusGainedAction(
                    editorRef = WeakReference(editor),
                    timeMillis = System.currentTimeMillis()
                )
            )
        }, this)
    }

    override fun dispose() {
        actionsHistory.clear()
    }

    private fun FeatureSuggester.isEnabled(): Boolean {
        return FeatureSuggesterSettings.instance().isEnabled(suggestingActionDisplayName)
    }
}