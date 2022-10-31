package ml.intellij.nlc.local.generation.search

import ml.intellij.nlc.local.generation.logSoftmax
import ml.intellij.nlc.local.generation.sliceArray
import ml.intellij.nlc.local.generation.topk1d
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

  override fun step(stepLogProbs: Array<DoubleArray>, context: IntArray?): Search.StepResult {
    val logProbs = logSoftmax(stepLogProbs)
    val logProbsLinearSize = logProbs.sumOf { it.size }
    val newLogProbs = DoubleArray(logProbsLinearSize)
    var offset = 0
    for (i in logProbs.indices) {
      val probs = logProbs[i]
      val score = hypothesesScores[i]
      for (value in probs) {
        val currentVal = value + score
        newLogProbs[offset] = currentVal
        offset++
      }
    }
    // TODO: hypotheses lost
    val newNumSamples = min(
      newLogProbs.count { it > Double.NEGATIVE_INFINITY },
      min(searchSize, logProbsLinearSize)
    )

    var samples = topk1d(newLogProbs, newNumSamples)
    val sampleScores = newLogProbs.sliceArray(samples)
    // TODO: inf mask -- diff flcc
    sortMask = IntArray(samples.size) { Math.floorDiv(samples[it], vocabSize) }
    samples = IntArray(samples.size) { Math.floorMod(samples[it], vocabSize) }

    //        initSortMask()
    updateState(samples, sampleScores, sortMask)
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
