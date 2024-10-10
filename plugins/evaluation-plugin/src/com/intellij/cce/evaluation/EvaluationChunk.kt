package com.intellij.cce.evaluation

import com.intellij.cce.core.Session
import com.intellij.cce.interpreter.InterpretFilter
import com.intellij.cce.interpreter.InterpretationHandler
import com.intellij.cce.interpreter.InterpretationOrder

/**
 * Represents a part of an evaluation which can be handled and represented separately.
 */
interface EvaluationChunk {
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