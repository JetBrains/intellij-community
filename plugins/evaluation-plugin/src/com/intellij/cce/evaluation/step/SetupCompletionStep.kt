// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step


import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.completion.CompletionType
import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.diagnostic.logger

class SetupCompletionStep(
  private val language: Language,
  private val completionType: CompletionType = CompletionType.BASIC,
  private val pathToZipModel: String? = null
) : UndoableEvaluationStep {
  private val popupParameterInfo = CodeInsightSettings.getInstance().AUTO_POPUP_PARAMETER_INFO

  override val name: String = "Setup completion step (deprecated)"
  override val description: String = "Turn on/off ML completion reordering if needed"

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace? {
    LOG.warn("SetupCompletionStep is deprecated and should not be used.")
    val ideaId = language.ideaLanguageId
    val ideaLanguage = com.intellij.lang.Language.findLanguageByID(ideaId)
    if (ideaLanguage == null) {
      LOG.info("Can't find idea language by id: $ideaId")
      return null
    }
    // to escape live lock
    CodeInsightSettings.getInstance().AUTO_POPUP_PARAMETER_INFO = false
    return workspace
  }


  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object : UndoableEvaluationStep.UndoStep {
      override val name: String = "Undo setup completion step"
      override val description: String = "Turn on/off ML completion reordering"

      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        CodeInsightSettings.getInstance().AUTO_POPUP_PARAMETER_INFO = popupParameterInfo
        return workspace
      }
    }
  }

  companion object {
    private val LOG = logger<SetupCompletionStep>()
  }
}
