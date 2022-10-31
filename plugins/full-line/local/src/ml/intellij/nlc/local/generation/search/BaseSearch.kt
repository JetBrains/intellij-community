package ml.intellij.nlc.local.generation.search

import ml.intellij.nlc.local.generation.slice
import ml.intellij.nlc.local.generation.sliceArray

abstract class BaseSearch(
    val vocabSize: Int,
    val searchSize: Int,
) : Search {
    override var hypothesesTokens: List<MutableList<Int>> = listOf(mutableListOf())
        protected set

    override var hypothesesScores: MutableList<Double> = mutableListOf(0.0)
        protected set

    lateinit var sortMask: IntArray
        protected set

    protected fun updateState(samples: IntArray, sampleScores: DoubleArray, sortMask: IntArray) {
        applySliceToState(sortMask)

        hypothesesScores = sampleScores.toMutableList()
        for (i in hypothesesTokens.indices) {
            hypothesesTokens[i].add(samples[i])
        }
    }

    protected fun applySliceToState(tensorSlice: IntArray) {
        hypothesesScores = hypothesesScores.slice(tensorSlice).toMutableList()
        hypothesesTokens = tensorSlice.map { ArrayList(hypothesesTokens[it]) }
        sortMask = sortMask.sliceArray(tensorSlice)
    }
}
