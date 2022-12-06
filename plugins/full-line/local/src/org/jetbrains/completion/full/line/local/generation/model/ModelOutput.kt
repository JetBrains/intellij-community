package org.jetbrains.completion.full.line.local.generation.model

import io.kinference.ndarray.arrays.NDArray

data class ModelOutput(val logits: Array<DoubleArray>, val pastStates: List<NDArray>) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ModelOutput

    if (!logits.contentDeepEquals(other.logits)) return false
    if (pastStates != other.pastStates) return false

    return true
  }

  override fun hashCode(): Int {
    var result = logits.contentDeepHashCode()
    result = 31 * result + pastStates.hashCode()
    return result
  }
}
