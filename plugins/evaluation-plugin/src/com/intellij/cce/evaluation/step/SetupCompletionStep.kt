// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step


import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.completion.CompletionType
import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.completion.ml.experiments.ExperimentStatus
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.completion.ml.sorting.RankingSupport
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import java.io.FileWriter

class SetupCompletionStep(private val language: Language,
                          private val completionType: CompletionType = CompletionType.BASIC,
                          private val pathToZipModel: String? = null) : UndoableEvaluationStep {
  companion object {
    private val LOG = logger<SetupCompletionStep>()
    private const val MODEL_ZIP_PATH_REGISTRY = "completion.ml.path.to.zip.model"
  }

  private val settings = CompletionMLRankingSettings.getInstance()
  private var initialRankingValue = false
  private var initialLanguageValue = false
  private var initialPathToZipModel = ""
  private val popupParameterInfo = CodeInsightSettings.getInstance().AUTO_POPUP_PARAMETER_INFO

  override val name: String = "Setup completion step"
  override val description: String = "Turn on/off ML completion reordering if needed"

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace? {
    val ideaId = language.ideaLanguageId
    val ideaLanguage = com.intellij.lang.Language.findLanguageByID(ideaId)
    if (ideaLanguage == null) {
      LOG.info("Can't find idea language by id: $ideaId")
      return null
    }
    // to escape live lock
    CodeInsightSettings.getInstance().AUTO_POPUP_PARAMETER_INFO = false
    initialRankingValue = settings.isRankingEnabled
    initialLanguageValue = settings.isLanguageEnabled(language.name)
    initialPathToZipModel = Registry.stringValue(MODEL_ZIP_PATH_REGISTRY)
    setMLCompletion(completionType == CompletionType.ML)
    if (pathToZipModel != null) {
      Registry.get(MODEL_ZIP_PATH_REGISTRY).setValue(pathToZipModel)
      val localModel = RankingSupport.loadLocalDebugModel(ideaLanguage)
      if (localModel == null) {
        System.err.println("Evaluation failed: failed to load ranking model, check pathToModelZip config field")
        return null
      }
    }
    else {
      Registry.get(MODEL_ZIP_PATH_REGISTRY).resetToDefault()
    }
    LOG.runAndLogException { dumpMlModelInfo(workspace, ideaLanguage) }
    return workspace
  }

  private fun dumpMlModelInfo(workspace: EvaluationWorkspace, ideaLanguage: com.intellij.lang.Language) {
    val rankingModel = RankingSupport.getRankingModel(ideaLanguage)
    val experimentVersion = ExperimentStatus.getInstance().forLanguage(ideaLanguage)
    if (rankingModel == null) {
      println("ML ranking disabled. Experiment version: ${experimentVersion.version}")
    } else {
      println("ML Ranking enabled. Model version: ${rankingModel.version()}. Experiment version: ${experimentVersion.version}")
      val jarWithModel = "completion-ranking-${StringUtil.toLowerCase(language.name)}-${rankingModel.version()}"
      FileWriter(workspace.path().resolve("ml_model_version.txt").toString()).use { it.write(jarWithModel) }
    }
  }

  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object : UndoableEvaluationStep.UndoStep {
      override val name: String = "Undo setup completion step"
      override val description: String = "Turn on/off ML completion reordering"

      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        CodeInsightSettings.getInstance().AUTO_POPUP_PARAMETER_INFO = popupParameterInfo
        setMLCompletion(initialRankingValue, initialLanguageValue)
        Registry.get(MODEL_ZIP_PATH_REGISTRY).setValue(initialPathToZipModel)
        return workspace
      }
    }
  }

  private fun setMLCompletion(rankingValue: Boolean, languageValue: Boolean = rankingValue) {
    settings.setLanguageEnabled(language.displayName, languageValue)
    settings.isRankingEnabled = rankingValue
  }
}
