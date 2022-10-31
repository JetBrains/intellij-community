package ml.intellij.nlc.local.generation.search

import kotlin.math.exp

interface Search {
  val hypothesesTokens: List<List<Int>>

  val hypothesesScores: List<Double>

  val hypotheses: List<Hypothesis>
    get() = hypothesesTokens.zip(hypothesesScores).map { Hypothesis(it.first.toIntArray(), exp(it.second)) }

  val batchSize: Int
    get() {
      assert(hypothesesScores.size == hypothesesTokens.size)
      return hypothesesScores.size
    }

  fun step(stepLogProbs: Array<DoubleArray>, context: IntArray?): StepResult

  data class StepResult(val sortMask: IntArray, val newTokens: IntArray)

  data class Hypothesis(val ids: IntArray, val score: Double)
}
