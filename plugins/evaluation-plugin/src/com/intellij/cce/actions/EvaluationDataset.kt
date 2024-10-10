package com.intellij.cce.actions

import com.intellij.cce.core.Session
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.interpreter.InterpretFilter
import com.intellij.cce.interpreter.InterpretationHandler
import com.intellij.cce.interpreter.InterpretationOrder
import com.intellij.cce.util.Progress

/**
 * Represents data which will be used for evaluation.
 */
interface EvaluationDataset { // FIXME with implicit invoker inside it may make sense to merge this interface into EvaluationEnvironment
  val setupSdk: EvaluationStep?
  val checkSdk: EvaluationStep?

  val preparationDescription: String

  fun prepare(datasetContext: DatasetContext, progress: Progress)

  fun sessionCount(datasetContext: DatasetContext): Int

  // TODO should return something closeable for large files
  fun chunks(datasetContext: DatasetContext): Iterator<EvaluationDatasetChunk>
}

interface EvaluationDatasetChunk {
  val datasetName: String
  val name: String
  val presentationText: String

  fun evaluate(
    handler: InterpretationHandler,
    filter: InterpretFilter,
    order: InterpretationOrder,
    sessionHandler: (Session) -> Unit
  ): List<Session>
}