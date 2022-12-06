package org.jetbrains.completion.full.line.local.generation.model

import io.kinference.ndarray.arrays.NDArray

data class ModelOutputSeq(val logits: List<Array<DoubleArray>> = emptyList(), val pastStates: List<NDArray> = emptyList()) {
  fun lastLogProbs(): ModelOutput {
    val logits: Array<DoubleArray> = Array(logits.size) { logits[it].last() }
    return ModelOutput(logits, pastStates)
  }
}
