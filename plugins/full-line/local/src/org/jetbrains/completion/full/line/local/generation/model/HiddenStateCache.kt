package org.jetbrains.completion.full.line.local.generation.model

import io.kinference.ndarray.arrays.NDArray
import io.kinference.ndarray.arrays.slice
import org.jetbrains.completion.full.line.local.TooShortAllowedContextLength
import org.jetbrains.completion.full.line.local.generation.generation.FullLineGenerationConfig
import kotlin.math.max
import kotlin.math.min

open class HiddenStateCache {
  private var cachedItem: Item? = null

  open fun onCacheHit(commonPrefixLength: Int) {}

  internal fun query(inputIds: IntArray): QueryResult {
    var commonPrefixLength = 0
    val queryResult = cachedItem?.let {
      // TODO: combine the logic with decision of outdating cache with composeInputIds
      commonPrefixLength = inputIds.commonPrefixLengthWith(it.inputIds)
      if (inputIds.size == it.inputIds.size && inputIds.size == commonPrefixLength) {
        return@let QueryResult(inputIds, null, modelOutput = it.modelOutput, false)
      }
      val pastStatesInfo = processPastStates(
        inputIds, commonPrefixLength, it.inputIds, it.modelOutput.pastStates
      )
      if (pastStatesInfo != null) {
        val (processedPastStates, processedContextLen, croppedPast) = pastStatesInfo
        val newInputIds = inputIds.slice(processedContextLen until inputIds.size).toIntArray()
        return@let QueryResult(newInputIds, processedPastStates, null, !croppedPast)
      }
      return@let null
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

  fun composeInputIds(metaInfoIds: IntArray, contextIds: IntArray, maxModelContextLength: Int, config: FullLineGenerationConfig): IntArray {
    // TODO: formalize through OOP the fact that it's GPT2 specific!
    // Detecting cache hit
    val cacheHit = cachedItem?.let {
      if (metaInfoIds.commonPrefixLengthWith(it.inputIds) != metaInfoIds.size) return@let null
      findCausalCacheHit(it.inputIds.slice(metaInfoIds.size until it.inputIds.size).toIntArray(), contextIds)
    }
    val maxContextLength = maxModelContextLength - metaInfoIds.size - config.maxLen
    val desiredContextLength = maxModelContextLength - metaInfoIds.size - config.maxLen - (config.contextOffsetForCache * maxModelContextLength).toInt()
    if (cacheHit != null && contextIds.size - cacheHit.index < maxContextLength) {
      // Check if it's not too short
      val cacheHitLength = metaInfoIds.size + cacheHit.value
      //val needToCompute = contextIds.size - (cacheHit.index + cacheHit.value)
      val missingContext = cacheHit.index - max(contextIds.size - desiredContextLength, 0)
      // TODO: tune the constants
      if (cacheHitLength > 5 && missingContext < 30) {
        return metaInfoIds + contextIds.slice(cacheHit.index until contextIds.size)
      }
    }

    if (desiredContextLength <= 0) throw TooShortAllowedContextLength("Desired context length is less than 0 with settings: " +
                                                                      "model max length: ${maxModelContextLength}, " +
                                                                      "metainfo size: ${metaInfoIds.size}, " +
                                                                      "num iters: ${config.maxLen}, " +
                                                                      "cache offset: ${config.contextOffsetForCache}")
    return metaInfoIds + contextIds.takeLast(min(desiredContextLength, contextIds.size))
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
      firstDifferentIndex = min(this.size, another.size)
    }

    return firstDifferentIndex
  }

  /**
   * Finds cache hit for Causal models (such as GPT or Transformer-XL).
   *
   * Causality of models restricts cropping of cache from the left to match the context.
   * If we do such a crop, cropped tokens already affected the rest of hidden states because of causality.
   *
   * @param cacheIds Ids that were cached
   * @param newIds Contexts ids that need to be aligned (cropped from left) to hit the cache
   * @return IndexedValue of max cache hit length, i.e (Index in newIds where the max cache hit starts, Length of max cache hit)
   */
  private fun findCausalCacheHit(cacheIds: IntArray, newIds: IntArray): IndexedValue<Int>? {
    // What we want (denote cacheIds as c and newIds as n):
    // n: |===========|   ->  n:    ---|===========|
    // c:    |====|           c:       |====|++++++
    // Good, we can update the cache with suffix of n saving some computations

    // n: |===========|   ->  n: ---|========|
    // c:   |===========|     c:    |========|--
    // Good, we can crop the cache and reuse it

    // n:   |===========| -x> Causal basically means we cannot crop hidden states (i.e. cache) from left
    // c:  |=========|
    // Bad, the cache contains some extra Ids in prefix that affect the rest hidden states

    // n:   |=========|
    // c:  |===========|
    // Bad, for the same reason as previous

    val zArr = zFunction(cacheIds, newIds)
    return zArr.slice(cacheIds.size + 1 until zArr.size).withIndex().maxByOrNull { it.value }
  }

  private fun zFunction(a: IntArray, b: IntArray): IntArray {
    val arr = IntArray(a.size + b.size + 1)
    for ((i, el) in a.withIndex()) {
      arr[i] = el
    }
    arr[a.size] = Int.MIN_VALUE
    for ((i, el) in b.withIndex()) {
      arr[i + a.size + 1] = el
    }

    val z = IntArray(arr.size)
    var l = 0
    var r = 0
    for (i in 1 until arr.size) {
      if (i <= r) {
        z[i] = min(r - i, z[i - l])
      }
      while (i + z[i] < arr.size && arr[z[i]] == arr[i + z[i]]) {
        z[i]++
      }
      if (i + z[i] > r) {
        l = i
        r = i + z[i]
      }
    }
    return z
  }
}
