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

  val sessionsExist: Boolean get() = true

  fun evaluate(
    handler: InterpretationHandler,
    filter: InterpretFilter,
    order: InterpretationOrder,
    sessionHandler: (Session) -> Unit
  ): Result

  fun iterate(iterationCount: Int): List<EvaluationChunk> = List(iterationCount) { iteration ->
    val iterationLabel = if (iterationCount > 1) "Iteration $iteration" else ""
    if (iterationLabel.isNotEmpty()) IterationChunk("${name} - $iterationLabel", this) else this
  }

  data class Result(
    val sessions: List<Session>,
    val presentationText: String?
  )
}

private class IterationChunk(override val name: String, private val chunk: EvaluationChunk) : EvaluationChunk by chunk