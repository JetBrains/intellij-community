package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace

class SetupFullLineStep : UndoableEvaluationStep {
  private var initLoggingEnabledValue: Boolean = false
  private var initLogPathValue: String? = null

  override val name: String = "Setup FullLine plugin step"
  override val description: String = "Enable FullLine BeamSearch logging"

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    initLoggingEnabledValue = java.lang.Boolean.parseBoolean(System.getProperty(LOGGING_ENABLED_PROPERTY, "false"))
    initLogPathValue = System.getProperty(LOG_PATH_PROPERTY)
    System.setProperty(LOGGING_ENABLED_PROPERTY, "true")
    return workspace
  }

  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object : UndoableEvaluationStep.UndoStep {
      override val name: String = "Undo setup FullLine step"
      override val description: String = "Return default behaviour of FullLine plugin"

      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        System.setProperty(LOGGING_ENABLED_PROPERTY, initLoggingEnabledValue.toString())
        val logPath = initLogPathValue
        if (logPath == null) {
          System.clearProperty(LOG_PATH_PROPERTY)
        } else {
          System.setProperty(LOG_PATH_PROPERTY, logPath)
        }
        return workspace
      }
    }
  }

  companion object {
    private const val LOGGING_ENABLED_PROPERTY = "flcc_search_logging_enabled"
    private const val LOG_PATH_PROPERTY = "flcc_search_log_path"
  }
}
