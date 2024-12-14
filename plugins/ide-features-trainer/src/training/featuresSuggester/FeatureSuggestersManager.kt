// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.featuresSuggester

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.EditorFocusGainedAction
import training.featuresSuggester.settings.FeatureSuggesterSettings
import training.featuresSuggester.suggesters.FeatureSuggester
import training.featuresSuggester.ui.NotificationSuggestionPresenter
import training.featuresSuggester.ui.SuggestionPresenter

@Service(Service.Level.PROJECT)
internal class FeatureSuggestersManager(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
  private val suggestionPresenter: SuggestionPresenter = NotificationSuggestionPresenter()

  init {
    if (!project.isDefault) {
      initFocusListener()
    }
  }

  fun actionPerformed(action: Action) {
    try {
      handleAction(action)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (t: Throwable) {
      thisLogger().error("An error occurred during action processing: $action", t)
    }
  }

  private fun handleAction(action: Action) {
    val language = action.language
    val needSendStatisticsForSwitchedOffCheckers = FeatureSuggesterSettings.instance().needSendStatisticsForSwitchedOffCheckers
    val suggesters = FeatureSuggester.suggesters
      .filter { (it.forceCheckForStatistics && needSendStatisticsForSwitchedOffCheckers) || it.isEnabled() }
      .filter { it.languages.find { id -> id == Language.ANY.id || id == language?.id } != null }
    for (suggester in suggesters) {
      processSuggester(suggester, action)
    }
  }

  private fun processSuggester(suggester: FeatureSuggester, action: Action) {
    val suggestion = suggester.getSuggestion(action)
    if (suggestion is UiSuggestion) {
      suggester.logStatisticsThatSuggestionIsFound(suggestion)
      if (suggester.isEnabled() && (SuggestingUtils.forceShowSuggestions || suggester.isSuggestionNeeded())) {
        when(suggestion) {
          is PopupSuggestion ->
            suggestionPresenter.showSuggestion(project, suggestion, coroutineScope = coroutineScope)
          is CustomSuggestion ->
            suggestion.activate()
        }
        fireSuggestionFound(suggestion)
        FeatureSuggesterSettings.instance().updateSuggestionShownTime(suggestion.suggesterId)
      }
    }
  }

  private fun fireSuggestionFound(suggestion: UiSuggestion) {
    // send event for testing
    project.messageBus.syncPublisher(FeatureSuggestersManagerListener.TOPIC).featureFound(suggestion)
  }

  private fun initFocusListener() {
    val eventMulticaster = EditorFactory.getInstance().eventMulticaster as? EditorEventMulticasterEx
    eventMulticaster?.addFocusChangeListener(
      object : FocusChangeListener {
        override fun focusGained(editor: Editor) {
          if (editor.project != project || !SuggestingUtils.isActionsProcessingEnabled(project)) return
          actionPerformed(EditorFocusGainedAction(
            editor = editor,
            timeMillis = System.currentTimeMillis()
          ))
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
