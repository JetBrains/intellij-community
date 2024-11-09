package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.util.registry.Registry

class SetupCloudStep : UndoableEvaluationStep {
  override val name: String
    get() = "Setup Cloud Authentication step"
  override val description: String
    get() = "Set appropriate registry keys"

  private var tokenFromEnvOriginalValue: Boolean = false
  private var endpointTypeOriginalValue: String = "User"
  private var stagingOriginalValue: Boolean = false

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    Registry.get(TOKEN_FROM_ENV).also { tokenFromEnvOriginalValue = it.asBoolean() }.setValue(true)
    Registry.get(ENDPOINT_TYPE).also { endpointTypeOriginalValue = it.selectedOption ?: "User" }.selectedOption = System.getenv(EVAL_ENDPOINT_TYPE) ?: "Service"
    Registry.get(USE_STAGING_URL).also { stagingOriginalValue = it.asBoolean() }.setValue(true)
    println("Cloud Authentication is set up. \n $ENDPOINT_TYPE = ${Registry.stringValue(ENDPOINT_TYPE)} \n $USE_STAGING_URL = ${Registry.stringValue(USE_STAGING_URL)}")
    return workspace
  }

  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object: UndoableEvaluationStep.UndoStep {
      override val name: String
        get() = "Undo Cloud Authentication step"
      override val description: String
        get() = "Reset registry keys"

      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        Registry.get(TOKEN_FROM_ENV).setValue(tokenFromEnvOriginalValue)
        Registry.get(ENDPOINT_TYPE).selectedOption = endpointTypeOriginalValue
        Registry.get(USE_STAGING_URL).setValue(stagingOriginalValue)
        return workspace
      }
    }
  }

  companion object {
    private const val TOKEN_FROM_ENV = "llm.enable.grazie.token.from.environment.variable.or.file"
    private const val ENDPOINT_TYPE = "llm.endpoint.type"
    private const val EVAL_ENDPOINT_TYPE = "eval_llm_endpoint_type"
    private const val USE_STAGING_URL = "llm.use.grazie.staging.url"
  }
}