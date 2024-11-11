package com.intellij.cce.evaluation.step

import com.intellij.cce.actions.DatasetContext
import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.EvaluationEnvironment
import com.intellij.cce.execution.manager.CodeExecutionManager
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace

class CodeExecutionStep(
  private val config: Config,
  private val environment: EvaluationEnvironment,
  private val datasetContext: DatasetContext,
) : BackgroundEvaluationStep {
  override val name: String = "Executing code"

  override val description: String = "Executing AI-generated code"


  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    val language = config.actions?.language
    if (language == null)
      return workspace

    CodeExecutionManager.getForLanguage(Language.resolve(language))?.let{ manager: CodeExecutionManager ->
      manager.isTest = true
      val savedPath = manager.saveFile()
      val compilationLog = manager.compile()
      val executionLog = manager.execute()
    }

    CodeExecutionManager.getForLanguage(
      Language.resolve(language)
    )?.execute()
    return workspace
  }
}