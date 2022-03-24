package com.intellij.cce.evaluation.step

import com.intellij.cce.actions.CompletionType
import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.completion.ml.sorting.RankingSupport
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.text.StringUtil
import java.io.FileWriter

class SetupCompletionStep(private val language: String,
                          private val completionType: CompletionType) : UndoableEvaluationStep {
  companion object {
    private val LOG = logger<SetupCompletionStep>()
  }

  private val settings = CompletionMLRankingSettings.getInstance()
  private var initialRankingValue = false
  private var initialLanguageValue = false
  private val popupParameterInfo = CodeInsightSettings.getInstance().AUTO_POPUP_PARAMETER_INFO

  override val name: String = "Setup completion step"
  override val description: String = "Turn on/off ML completion reordering if needed"

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    // to escape live lock
    CodeInsightSettings.getInstance().AUTO_POPUP_PARAMETER_INFO = false
    initialRankingValue = settings.isRankingEnabled
    initialLanguageValue = settings.isLanguageEnabled(language)
    setMLCompletion(completionType == CompletionType.ML)

    LOG.runAndLogException { dumpMlModelInfo(workspace) }
    return workspace
  }

  private fun dumpMlModelInfo(workspace: EvaluationWorkspace) {
    val languageKind = Language.resolve(language)
    val ideaId = languageKind.ideaLanguageId
    val ideaLanguage = com.intellij.lang.Language.findLanguageByID(ideaId)
    if (ideaLanguage == null) {
      LOG.info("Can't find idea language by id: $ideaId")
      return
    }
    val rankingModel = RankingSupport.getRankingModel(ideaLanguage) ?: return
    val jarWithModel = "completion-ranking-${StringUtil.toLowerCase(languageKind.name)}-${rankingModel.version()}"
    FileWriter(workspace.path().resolve("ml_model_version.txt").toString()).use { it.write(jarWithModel) }
  }

  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object : UndoableEvaluationStep.UndoStep {
      override val name: String = "Undo setup completion step"
      override val description: String = "Turn on/off ML completion reordering"

      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        CodeInsightSettings.getInstance().AUTO_POPUP_PARAMETER_INFO = popupParameterInfo
        setMLCompletion(initialRankingValue, initialLanguageValue)
        return workspace
      }
    }
  }

  private fun setMLCompletion(rankingValue: Boolean, languageValue: Boolean = rankingValue) {
    settings.setLanguageEnabled(language, languageValue)
    settings.isRankingEnabled = rankingValue
  }
}