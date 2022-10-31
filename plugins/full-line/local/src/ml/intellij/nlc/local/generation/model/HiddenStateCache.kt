package ml.intellij.nlc.local.generation.model

import io.kinference.ndarray.arrays.NDArray
import io.kinference.ndarray.arrays.slice

open class HiddenStateCache {
  private var cachedItem: Item? = null

  open fun onCacheHit(commonPrefixLength: Int) {}

  internal fun query(inputIds: IntArray): QueryResult {
    var commonPrefixLength = 0
    val queryResult = cachedItem?.let {
      commonPrefixLength = inputIds.commonPrefixLengthWith(it.inputIds)
      if (commonPrefixLength < 5) {
        // It's probably a coincidence, we don't want to keep such a cache
        // TODO: prefer storing relatively long matching caches
        return@let null
      }
      else {
        if (inputIds.size == it.inputIds.size && inputIds.size == commonPrefixLength) {
          return@let QueryResult(inputIds, null, modelOutput = it.modelOutput, false)
        }
        val pastStatesInfo = processPastStates(
          inputIds, commonPrefixLength, it.inputIds, it.modelOutput.pastStates
        )
        if (pastStatesInfo != null) {
          val (processedPastStates, processedContextLen, croppedPast) = pastStatesInfo
          val newInputIds = inputIds.slice(IntRange(processedContextLen, inputIds.size - 1)).toIntArray()
          return@let QueryResult(newInputIds, processedPastStates, null, !croppedPast)
        }
        return@let null
      }
    } ?: QueryResult(inputIds, null, null, true)
    if (queryResult.pastStates != null || queryResult.modelOutput != null) {
      onCacheHit(commonPrefixLength)
    }
    return queryResult
  }

  internal fun cache(inputIds: IntArray, modelOutput: ModelOutput) {
    cachedItem = Item(inputIds, modelOutput)
  }

  internal fun reset() {
    cachedItem = null
  }

  private fun processPastStates(
    inputIds: IntArray, commonPrefixLength: Int, cachedInputIds: IntArray, cachedPastStates: List<NDArray>
  ): Triple<List<NDArray>, Int, Boolean>? {
    // If cached input ids is longer than common prefix, we need to crop past states
    if (cachedInputIds.size > commonPrefixLength) {
      var croppedPastStatesLength = commonPrefixLength
      // If new inputIds is a full prefix of cached inputIds, there'll be no new input
      // and KInference doesn't work that way, so we need to do something about it
      if (inputIds.size == commonPrefixLength) {
        // We'll make inputIds smaller by 1 token and put this token into model context
        // for it not to be empty, but we can't use this trick if the context is shorter than 2
        // In that case we'll say that there was no cache hit at all
        if (inputIds.size > 1) {
          croppedPastStatesLength--
        }
        else {
          return null
        }
      }
      return Triple(cropPastStates(cachedPastStates, croppedPastStatesLength), croppedPastStatesLength, true)
    }
    else {
      return Triple(cachedPastStates, commonPrefixLength, false)
    }
  }

  private fun cropPastStates(pastStates: List<NDArray>, length: Int): List<NDArray> {
    return pastStates.map {
      if (it.shape[3] != length) {
        val newShape = it.shape.copyOf()
        newShape[3] = length
        it.slice(ends = newShape)
      }
      else {
        it
      }
    }
  }

  data class Item(val inputIds: IntArray, val modelOutput: ModelOutput)

  data class QueryResult(
    val newInputIds: IntArray,
    val pastStates: List<NDArray>?,
    val modelOutput: ModelOutput?,
    val cacheOutdated: Boolean
  )

  private fun IntArray.commonPrefixLengthWith(another: IntArray): Int {
    var firstDifferentIndex = this.zip(another).indexOfFirst { (a, b) -> a != b }
    if (firstDifferentIndex == -1) {
      firstDifferentIndex = kotlin.math.min(this.size, another.size)
    }

    return firstDifferentIndex
  }
}
