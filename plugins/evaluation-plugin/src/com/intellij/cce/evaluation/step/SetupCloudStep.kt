package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue

class SetupCloudStep : UndoableEvaluationStep {
  override val name: String
    get() = "Setup Cloud Authentication step"
  override val description: String
    get() = "Set appropriate registry keys"

  private var tokenFromEnvOriginalValue: Boolean? = null
  private var endpointTypeOriginalValue: String? = null
  private var stagingOriginalValue: Boolean? = null

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    start()
    return workspace
  }

  fun start() {
    setRegistryIfMissing(TOKEN_FROM_ENV, true) { tokenFromEnvOriginalValue = it.asBoolean() }
    setRegistryIfMissing(USE_STAGING_URL, true) { stagingOriginalValue = it.asBoolean() }
    setRegistryIfMissing(ENDPOINT_TYPE, System.getenv(EVAL_ENDPOINT_TYPE) ?: "Application") {
      endpointTypeOriginalValue = it.selectedOption ?: "User"
    }
    println("Cloud Authentication is set up. \n $ENDPOINT_TYPE = ${Registry.stringValue(ENDPOINT_TYPE)} \n $USE_STAGING_URL = ${
      Registry.stringValue(USE_STAGING_URL)
    }")
  }

  fun setRegistryIfMissing(name: String, value: Boolean, reader: (RegistryValue) -> Unit) {
    val existed = System.getProperty(name)
    if (existed == null) {
      Registry.get(name).also { reader.invoke(it) }.setValue(value)
    }
    else {
      println("Property $name is already provided as $existed")
    }
  }

  fun setRegistryIfMissing(name: String, value: String, reader: (RegistryValue) -> Unit) {
    val existed = System.getProperty(name)
    if (existed == null) {
      Registry.get(name).also { reader.invoke(it) }.selectedOption = value
    }
    else {
      println("Property $name is already provided as $existed")
    }
  }

  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object: UndoableEvaluationStep.UndoStep {
      override val name: String
        get() = "Undo Cloud Authentication step"
      override val description: String
        get() = "Reset registry keys"

      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        tokenFromEnvOriginalValue?.let { Registry.get(TOKEN_FROM_ENV).setValue(it) }
        endpointTypeOriginalValue?.let { Registry.get(ENDPOINT_TYPE).selectedOption = it }
        stagingOriginalValue?.let { Registry.get(USE_STAGING_URL).setValue(it) }
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