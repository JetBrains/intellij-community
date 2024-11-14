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
    //workspace.sessionsStorage.getSessions(workspace.sessionsStorage.getSessionFiles().get(0).first).sessions.
    //workspace.sessionsStorage.getSessionFiles().
    //workspace.sessionsStorage.getSessions(workspace.sessionsStorage.getSessionFiles().get(0).first).sessions[0].lookups[0].suggestions[0].text
    val sessionFiles = workspace.sessionsStorage.getSessionFiles()

    for (sessionFile in sessionFiles) {
      val session = workspace.sessionsStorage.getSessions(sessionFile.first).sessions[0]
      val code = session.lookups[0].suggestions[0].text
      CodeExecutionManager.getForLanguage(Language.resolve(language))?.let { manager: CodeExecutionManager ->
        manager.isTest = true
        manager.saveFile(code)
        val compilationLog = manager.compile()
        val executionLog = manager.execute(PathManager.getAbsolutePath(config.actions!!.projectPath))
      }
    }

    return workspace
  }
}