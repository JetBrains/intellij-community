package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.util.registry.Registry

class SetupRegistryStep(private val registry: String) : UndoableEvaluationStep {
  override val name: String
    get() = "Set registry keys"
  override val description: String
    get() = "Set appropriate registry keys"

  private val originalValues: MutableMap<String, String> = mutableMapOf()

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    if (registry.isBlank()) {
      return workspace
    }
    val pairs = registry.split(",")
    for (pair in pairs) {
      val (key, value) = pair.split("=").map { it.trim() }
      originalValues[key] = Registry.get(key).asString()
      println("Registry value for \"$key\" changed to \"${value}\"")
      Registry.get(key).setValue(value)
    }
    return workspace
  }

  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object: UndoableEvaluationStep.UndoStep {
      override val name: String
        get() = "Undo Setup Registry step"
      override val description: String
        get() = "Reset registry keys"

      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        for ((key, value) in originalValues) {
          Registry.get(key).setValue(value)
        }
        return workspace
      }
    }
  }
}
