package com.intellij.cce.evaluation

import com.intellij.cce.actions.DatasetContext
import com.intellij.cce.actions.DatasetRef
import com.intellij.cce.evaluation.step.runInIntellij
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.EvaluationWorkspace
import java.nio.file.Path

/**
 * Environment represents resources needed for an evaluation.
 * For example, it can be IntelliJ project like in ProjectActionsEnvironment if an evaluation needs an opened project.
 * It should be initialized before an evaluation process and closed right after finish.
 */
interface EvaluationEnvironment : AutoCloseable {

  val setupSdk: EvaluationStep?
  val checkSdk: EvaluationStep?

  val preparationDescription: String

  fun prepare(datasetContext: DatasetContext, progress: Progress)

  fun sessionCount(datasetContext: DatasetContext): Int

  // TODO should return something closeable for large files
  fun chunks(datasetContext: DatasetContext): Iterator<EvaluationChunk>

  fun execute(step: EvaluationStep, workspace: EvaluationWorkspace): EvaluationWorkspace?
}

abstract class SimpleFileEnvironment : EvaluationEnvironment {

  protected abstract val datasetRef: DatasetRef

  override val setupSdk: EvaluationStep? = null
  override val checkSdk: EvaluationStep? = null

  protected open fun checkFile(datasetPath: Path) {
  }

  override fun prepare(datasetContext: DatasetContext, progress: Progress) {
    datasetRef.prepare(datasetContext)
    val datasetPath = datasetContext.path(datasetRef)
    checkFile(datasetPath)
  }

  override fun execute(step: EvaluationStep, workspace: EvaluationWorkspace): EvaluationWorkspace? =
    step.runInIntellij(null, workspace)

  override fun close() {
  }
}