package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry

class DisableDockerEel : UndoableEvaluationStep {
  override val name: String = "Disable Docker Eel"
  override val description: String = "Quick-fix for ClassNotFoundException in docker environment"

  private val disposable = Disposer.newDisposable()
  private val registryKey = "vfs.fetch.case.sensitivity.using.eel"
  private var registryValue: Boolean = false

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    Registry.get(registryKey).also { registryValue = it.asBoolean() }.setValue(false)
    val ep = ApplicationManager.getApplication().extensionArea.getExtensionPointIfRegistered<Any>("com.intellij.eelProvider")
    @Suppress("TestOnlyProblems")
    (ep as? ExtensionPointImpl<*>)?.maskAll(emptyList(), disposable, true)
    return workspace
  }

  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object : UndoableEvaluationStep.UndoStep {
      override val name: String = "Undo Disable Docker Eel step"
      override val description: String = "Restores original state"
      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        Registry.get(registryKey).setValue(registryValue)
        Disposer.dispose(disposable)
        return workspace
      }
    }
  }
}