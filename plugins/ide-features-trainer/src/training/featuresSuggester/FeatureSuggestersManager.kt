// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.EditorFocusGainedAction
import training.featuresSuggester.settings.FeatureSuggesterSettings
import training.featuresSuggester.statistics.FeatureSuggesterStatistics
import training.featuresSuggester.suggesters.FeatureSuggester
import training.featuresSuggester.ui.NotificationSuggestionPresenter
import training.featuresSuggester.ui.SuggestionPresenter

class FeatureSuggestersManager(val project: Project) : Disposable {
  private val suggestionPresenter: SuggestionPresenter =
    NotificationSuggestionPresenter()

  init {
    if (!project.isDefault) initFocusListener()
  }

  fun actionPerformed(action: Action) {
    try {
      handleAction(action)
    }
    catch (t: Throwable) {
      thisLogger().error("An error occurred during action processing: $action", t)
    }
  }

  private fun handleAction(action: Action) {
    if (project.isDisposed || DumbService.isDumb(project)) return
    val language = action.language ?: return
    val suggesters = FeatureSuggester.suggesters
      .filter { it.languages.find { id -> id == Language.ANY.id || id == language.id } != null }
    for (suggester in suggesters) {
      if (suggester.isEnabled()) {
        processSuggester(suggester, action)
      }
    }
  }

  private fun processSuggester(suggester: FeatureSuggester, action: Action) {
    val suggestion = suggester.getSuggestion(action)
    if (suggestion is PopupSuggestion) {
      FeatureSuggesterStatistics.logSuggestionFound(suggester.id)
      if (forceShowSuggestions || suggester.isSuggestionNeeded()) {
        suggestionPresenter.showSuggestion(project, suggestion)
        fireSuggestionFound(suggestion)
        FeatureSuggesterSettings.instance().updateSuggestionShownTime(suggestion.suggesterId)
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
