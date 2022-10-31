package org.jetbrains.completion.full.line.local.generation.model

import io.kinference.ndarray.arrays.NDArray

data class ModelOutputSeq(val logProbs: List<Array<DoubleArray>> = emptyList(), val pastStates: List<NDArray> = emptyList()) {
  fun lastLogProbs(): ModelOutput {
    val lastProbs: Array<DoubleArray> = Array(logProbs.size) { logProbs[it].last() }
    return ModelOutput(lastProbs, pastStates)
  }
}
