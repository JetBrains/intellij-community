package ml.intellij.nlc.local.generation.model

import io.kinference.model.ExecutionContext
import io.kinference.ndarray.arrays.NDArray

interface ModelWrapper {
  val maxSeqLen: Int

  fun initLogProbs(inputIds: Array<IntArray>, execContext: ExecutionContext): ModelOutputSeq

  fun initLastLogProbs(inputIds: Array<IntArray>, execContext: ExecutionContext): ModelOutput {
    return initLogProbs(inputIds, execContext).lastLogProbs()
  }

  fun getLogProbs(inputIds: Array<IntArray>, past: List<NDArray>, execContext: ExecutionContext): ModelOutputSeq

  fun getLastLogProbs(inputIds: IntArray, past: List<NDArray>, execContext: ExecutionContext): ModelOutput {
    return getLogProbs(Array(inputIds.size) { intArrayOf(inputIds[it]) }, past, execContext).lastLogProbs()
  }
}
