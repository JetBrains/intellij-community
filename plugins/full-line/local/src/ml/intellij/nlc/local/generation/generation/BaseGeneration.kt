package ml.intellij.nlc.local.generation.generation

import io.kinference.model.ExecutionContext
import io.kinference.ndarray.arrays.IntNDArray
import ml.intellij.nlc.local.generation.logSoftmax
import ml.intellij.nlc.local.generation.matcher.FuzzyPrefixMatcher
import ml.intellij.nlc.local.generation.matcher.PrefixMatcher
import ml.intellij.nlc.local.generation.model.ModelWrapper
import ml.intellij.nlc.local.generation.search.Search
import ml.intellij.nlc.local.generation.slice
import ml.intellij.nlc.local.tokenizer.Tokenizer
import io.kinference.ndarray.arrays.NDArray
import io.kinference.ndarray.arrays.Strides
import io.kinference.ndarray.arrays.tiled.IntTiledArray
import kotlin.math.*
import com.intellij.openapi.project.Project

abstract class BaseGeneration<GenerationConfig : BaseGenerationConfig>(
    override val model: ModelWrapper, final override val tokenizer: Tokenizer
) : Generation<GenerationConfig> {
    data class PrefixInfo(val text: String, val errLimit: Int)

    internal open val prefixMatcher: PrefixMatcher = FuzzyPrefixMatcher(tokenizer)

    private var prefixes: List<PrefixInfo>? = null
    private var mems: List<NDArray>? = null
    protected var eachStepProbs: List<MutableList<Double>> = listOf(ArrayList())
    protected var nextLogProbs: Array<DoubleArray>? = null

    protected val vocabSize: Int
        get() = tokenizer.vocabSize

    private var logSpellProb = ln(0.0001)


    private fun maskPrefixes(scores: Array<DoubleArray>): Array<DoubleArray> {
        prefixes?.forEachIndexed { i, (prefix, err_limit) ->
            if (prefix.isEmpty()) return@forEachIndexed

            val prefixIndsByErr = prefixMatcher.prefixTokensByErr(prefix, err_limit)
            for (j in prefixIndsByErr[0]) {
                try {
                    scores[i][j] = Double.NEGATIVE_INFINITY
                } catch (e: ArrayIndexOutOfBoundsException) {
                    throw IllegalArgumentException("Argument does not match object state", e)
                }
            }

            for (err_num in 1 until prefixIndsByErr.size) {
                val prefixToken = prefixIndsByErr[err_num]
                for (j in prefixToken) {
                    scores[i][j] = scores[i][j] + (err_num - 1) * logSpellProb
                }
            }

            // ban tokens with bad symbols
//            for (j in tokenizer.invalidIds) {
//                scores[i][j] = Double.NEGATIVE_INFINITY
//            }
        } ?: initFail()

        return scores
    }

    protected fun initLogProbs(context: IntArray, execContext: ExecutionContext) {
        val logProbs = model.initLastLogProbs(arrayOf(context), execContext)
        mems = logProbs.pastStates
        nextLogProbs = maskPrefixes(logSoftmax(logProbs.logProbs))
    }

    protected fun initState(prefix: String, config: GenerationConfig) {
        logSpellProb = ln(config.spellProb)
        prefixes = listOf(PrefixInfo(prefix, config.prefixErrLimit))
    }

    protected fun sortState(sortMask: IntArray) {
        // mems = [mem[:, sort_mask].contiguous() for mem in mems]
        val indices = IntNDArray(IntTiledArray(arrayOf(sortMask)), Strides(intArrayOf(sortMask.size)))
        mems = mems?.map { mem ->
            mem.gather(indices, axis = 1)
        } ?: initFail()

        eachStepProbs = sortMask.map { ArrayList(eachStepProbs[it]) }
        try {
            prefixes = prefixes?.slice(sortMask) ?: initFail()
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalArgumentException("Argument does not match object state", e)
        }
    }

    protected fun updateState(sortMask: IntArray, newTokensIds: IntArray) {
        sortState(sortMask)

        sortMask.zip(newTokensIds).forEachIndexed { index, (batchInd, tokenInd) ->
            eachStepProbs[index].add(exp((nextLogProbs ?: initFail())[batchInd][tokenInd]))
        }

        updatePrefix(newTokensIds)
    }

    protected fun updateLogProbs(data: IntArray, execContext: ExecutionContext) {
        val logProbs = model.getLastLogProbs(data, mems ?: initFail(), execContext)
        mems = logProbs.pastStates
        nextLogProbs = maskPrefixes(logSoftmax(logProbs.logProbs))
    }

    private fun updatePrefix(newTokensIds: IntArray) {
        prefixes?.let {
            val result = ArrayList<PrefixInfo>(it.size)

            it.forEachIndexed { i, (prefix, errLimit) ->
                val tokenId = newTokensIds[i]
                val token = tokenizer.decode(tokenId)
                val errCnt = PrefixMatcher.levenshtein(prefix, token)
                val newPrefix = prefix.substring(min(prefix.length, token.length))
                result.add(PrefixInfo(newPrefix, min(errLimit - errCnt, newPrefix.length)))
            }

            prefixes = result
        } ?: initFail()
    }

    protected fun resetState() {
        mems = null
        prefixes = null
    }

    protected fun currentHypotheses(search: Search): List<GenerationInfo> {
        return search.hypothesesTokens.zip(eachStepProbs).map { (hyp, probs) -> GenerationInfo(probs, hyp) }
    }

    private fun initFail(): Nothing {
        throw IllegalStateException("Object state have not been initialized yet. Call initState and initLogProbs first.")
    }
}
