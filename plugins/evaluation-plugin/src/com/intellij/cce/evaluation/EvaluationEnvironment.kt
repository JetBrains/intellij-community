package com.intellij.cce.evaluation

import com.intellij.cce.actions.DatasetContext
import com.intellij.cce.actions.DatasetRef
import com.intellij.cce.evaluation.data.Bindable
import com.intellij.cce.evaluation.data.Binding
import com.intellij.cce.evaluation.step.runInIntellij
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.EvaluationWorkspace

/**
 * Environment represents resources needed for an evaluation.
 * For example, it can be IntelliJ project like in ProjectActionsEnvironment if an evaluation needs an opened project.
 * It should be initialized before an evaluation process and closed right after finish.
 */
interface EvaluationEnvironment : AutoCloseable {

  val setupSteps: List<EvaluationStep>

  val preparationDescription: String

  fun initialize(datasetContext: DatasetContext)

  fun prepareDataset(datasetContext: DatasetContext, progress: Progress)

  fun sessionCount(datasetContext: DatasetContext): Int

  // TODO should return something closeable for large files
  fun chunks(datasetContext: DatasetContext): Sequence<EvaluationChunk>

  fun execute(step: EvaluationStep, workspace: EvaluationWorkspace): EvaluationWorkspace?

  // place here just for convenience, should be final protected by meaning
  infix fun <T, B : Bindable<T>> B.bind(value: T): Binding<B> = Binding.create(this, value)
}

interface SimpleFileEnvironment : EvaluationEnvironment {

  val datasetRef: DatasetRef

  override val setupSteps: List<EvaluationStep> get() = emptyList()

  override val preparationDescription: String get() = "Checking that dataset file is available"

  override fun initialize(datasetContext: DatasetContext) {
    datasetRef.prepare(datasetContext)
  }

  override fun prepareDataset(datasetContext: DatasetContext, progress: Progress) {
  }

  override fun execute(step: EvaluationStep, workspace: EvaluationWorkspace): EvaluationWorkspace? =
    step.runInIntellij(null, workspace)

  override fun close() {
  }
}