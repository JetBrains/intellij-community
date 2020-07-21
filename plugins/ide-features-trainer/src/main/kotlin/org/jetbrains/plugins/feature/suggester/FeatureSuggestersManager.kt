package org.jetbrains.plugins.feature.suggester

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.actions.listeners.DocumentActionsListener
import org.jetbrains.plugins.feature.suggester.actions.listeners.EditorActionsListener
import org.jetbrains.plugins.feature.suggester.actions.listeners.PsiActionsListener
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings

class FeatureSuggestersManager(val project: Project) : FileEditorManagerListener {
    private val MAX_ACTIONS_NUMBER: Int = 100
    private val actionsHistory = UserActionsHistory(MAX_ACTIONS_NUMBER)
    private val suggestionPresenter: SuggestionPresenter = NotificationSuggestionPresenter()

    private var listenersIsSet: Boolean = false

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

    override fun fileOpened(
        source: FileEditorManager,
        file: VirtualFile
    ) {
        if (project != source.project || listenersIsSet)
            return

        PsiManager.getInstance(project).addPsiTreeChangeListener(PsiActionsListener(this::actionPerformed), project)

        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(AnActionListener.TOPIC, EditorActionsListener(this::actionPerformed))

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            DocumentActionsListener(
                project,
                this::actionPerformed
            ), project
        )

        listenersIsSet = true
    }

    private fun FeatureSuggester.isEnabled(): Boolean {
        return FeatureSuggesterSettings.isEnabled(getId())
    }
}