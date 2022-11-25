package org.jetbrains.completion.full.line.local.generation.search

import org.jetbrains.completion.full.line.local.generation.LapTimer
import org.jetbrains.completion.full.line.local.generation.logSoftmax
import org.jetbrains.completion.full.line.local.generation.sliceArray
import org.jetbrains.completion.full.line.local.generation.topk1d
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

class FullLineBeamSearch(
  vocabSize: Int,
  searchSize: Int,
  private val lenNormBase: Double = 0.0,
  private val lenNormPow: Double = 0.0,
) : BaseSearch(
  vocabSize, searchSize
) {
  private var length = 1

  override val hypotheses: List<Search.Hypothesis>
    get() = hypothesesTokens.zip(getExpNormalizedScores()).map { (hypothesisTokens, hypothesisScore) ->
      Search.Hypothesis(
        hypothesisTokens.toIntArray(),
        hypothesisScore
      )
    }

  val lastPredictions: IntArray
    get() {
      assert(
        hypothesesTokens.isNotEmpty() && hypothesesTokens[0].size > 0
      ) { "Can't get last predictions if there is no hypotheses" }
      return IntArray(hypothesesTokens.size) { hypothesesTokens[it].last() }
    }

  override fun step(stepLogProbs: Array<DoubleArray>, context: IntArray?, timer: LapTimer?): Search.StepResult {
    val logProbsLinearSize = stepLogProbs.sumOf { it.size }
    timer?.lap("BeamSearch step: softmax")
    val newLogProbs = DoubleArray(logProbsLinearSize)
    var offset = 0
    for (i in stepLogProbs.indices) {
      val probs = stepLogProbs[i]
      val score = hypothesesScores[i]
      for (value in probs) {
        newLogProbs[offset] = value + score
        offset++
      }
    }
    timer?.lap("BeamSearch step: add cur logProbs to past")
    // TODO: hypotheses lost
    val newNumSamples = min(
      newLogProbs.count { it > Double.NEGATIVE_INFINITY },
      min(searchSize, logProbsLinearSize)
    )
    timer?.lap("BeamSearch step: count numSamples")

    var samples = topk1d(newLogProbs, newNumSamples)
    timer?.lap("BeamSearch step: topk1d")
    val sampleScores = newLogProbs.sliceArray(samples)
    timer?.lap("BeamSearch step: slice scores")
    // TODO: inf mask -- diff flcc
    sortMask = IntArray(samples.size) { Math.floorDiv(samples[it], vocabSize) }
    samples = IntArray(samples.size) { Math.floorMod(samples[it], vocabSize) }
    timer?.lap("BeamSearch step: divmod samples")

    //        initSortMask()
    updateState(samples, sampleScores, sortMask)
    timer?.lap("BeamSearch step: updateState")
    length += 1

    return Search.StepResult(sortMask, lastPredictions)
  }

  fun dropHypotheses(indices: List<Int>): List<Search.Hypothesis> {
    val droppedHypotheses = indices.map { hypotheses.elementAt(it) }
    val newSortMask = hypothesesScores.indices.filter { !indices.contains(it) }.toIntArray()
    applySliceToState(newSortMask)
    sortMask = newSortMask
    val topBsMask = topk1d(hypothesesScores.toDoubleArray(), min(searchSize, hypothesesScores.size))
    applySliceToState(topBsMask)
    return droppedHypotheses
  }

  private fun getExpNormalizedScores(): List<Double> {
    val normFactor = ((lenNormBase + length) / (lenNormBase + 1)).pow(lenNormPow)
    return DoubleArray(hypothesesScores.size) { exp(hypothesesScores[it] / normFactor) }.toList()
  }
}
