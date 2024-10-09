package com.intellij.cce.evaluation

import com.intellij.cce.actions.EvaluationDataset
import com.intellij.cce.evaluation.step.runInIntellij
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.workspace.EvaluationWorkspace

/**
 * Environment represents resources needed for an evaluation.
 * For example, it can be IntelliJ project like in [ProjectEnvironment] if an evaluation needs an opened project.
 * It should be initialized before an evaluation process and closed right after finish.
 */
interface EvaluationEnvironment : AutoCloseable {
  val dataset: EvaluationDataset

  fun execute(step: EvaluationStep, workspace: EvaluationWorkspace): EvaluationWorkspace?
}

/**
 * A special type of environment which doesn't imply any associated resources and can be treated like a simple data class.
 */
class StandaloneEnvironment(
  override val dataset: EvaluationDataset,
) : EvaluationEnvironment {
  override fun execute(step: EvaluationStep, workspace: EvaluationWorkspace): EvaluationWorkspace? =
    step.runInIntellij(null, workspace)

  override fun close() {
  }
}