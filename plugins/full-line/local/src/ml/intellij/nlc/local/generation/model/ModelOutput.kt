package ml.intellij.nlc.local.generation.model

import io.kinference.ndarray.arrays.NDArray

data class ModelOutput(val logProbs: Array<DoubleArray>, val pastStates: List<NDArray>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModelOutput

        if (!logProbs.contentDeepEquals(other.logProbs)) return false
        if (pastStates != other.pastStates) return false

        return true
    }

    override fun hashCode(): Int {
        var result = logProbs.contentDeepHashCode()
        result = 31 * result + pastStates.hashCode()
        return result
    }
}
