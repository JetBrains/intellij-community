package org.jetbrains.completion.full.line.local.generation

import java.util.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.random.Random

internal fun IntArray.toLongArray(): LongArray {
  return LongArray(size) { this[it].toLong() }
}

internal fun Array<IntArray>.toLongArray(): LongArray {
  val arr = LongArray(this.sumOf { it.size })
  var off = 0
  for (block in this) {
    for (value in block) arr[off++] = value.toLong()
  }
  return arr
}

internal fun IntArray.sliceArray(indices: IntArray): IntArray {
  val result = IntArray(indices.size)
  var targetIndex = 0
  for (sourceIndex in indices) {
    result[targetIndex++] = this[sourceIndex]
  }
  return result
}

internal fun DoubleArray.sliceArray(indices: IntArray): DoubleArray {
  val result = DoubleArray(indices.size)
  var targetIndex = 0
  for (sourceIndex in indices) {
    result[targetIndex++] = this[sourceIndex]
  }
  return result
}

internal fun <T> List<T>.slice(indices: IntArray): List<T> {
  val result = ArrayList<T>(indices.size)
  for ((targetIndex, sourceIndex) in indices.withIndex()) {
    result.add(targetIndex, this[sourceIndex])
  }
  return result
}

internal fun logSoftmax(scores: Array<DoubleArray>): Array<DoubleArray> {
  val expScores = Array(scores.size) {
    val curScores = scores[it]
    DoubleArray(curScores.size) { i -> exp(curScores[i]) }
  }
  for (score in expScores) {
    val scoresSum = score.sum()
    for (i in score.indices) score[i] = ln(score[i] / scoresSum)
  }
  return expScores
}

internal fun topk1d(data: DoubleArray, size: Int): IntArray {
  if (size == 0) {
    return IntArray(0)
  }
  val filteredData = data.filter { it > Double.NEGATIVE_INFINITY }.toMutableList()
  val kthLargestValue = if (size <= filteredData.size) kthLargest(filteredData, size) else Double.NEGATIVE_INFINITY
  return data
    .mapIndexed { i, v -> Pair(i, v) }
    .filter { it.second >= kthLargestValue }
    .take(size)
    .sortedBy { -it.second }
    .map { it.first }
    .toIntArray()
}

private fun kthLargest(arr: MutableList<Double>, k: Int): Double {
  assert(k > 0 && k <= arr.size)
  return quickSelect(arr, arr.size - k)
}

private fun quickSelect(arr: MutableList<Double>, k: Int): Double {
  var left = 0
  var right = arr.size - 1
  while (true) {
    if (left >= right) return arr[left]
    val partitionIndex = partition(arr, left, right, left + (right - left) / 2)
    when {
      k < partitionIndex -> right = partitionIndex - 1
      k > partitionIndex -> left = partitionIndex + 1
      else -> return arr[k]
    }
  }
}

private fun partition(arr: MutableList<Double>, left: Int, right: Int, partitionIndex: Int): Int {
  val partVal = arr[partitionIndex]
  Collections.swap(arr, partitionIndex, right)
  var resultIndex = left
  for (i in left until right) {
    if (arr[i] < partVal) {
      Collections.swap(arr, resultIndex++, i)
    }
  }
  Collections.swap(arr, resultIndex, right)
  return resultIndex
}

internal fun topk2d(data: Array<DoubleArray>, size: Int, dim: Int = 0): Array<IntArray> {
  if (data.isEmpty()) {
    return emptyArray()
  }

  when (dim) {
    0 -> {
      val listSize = min(data.size, size)
      val result = Array(listSize) { IntArray(data[0].size) }
      for (j in data[0].indices) {
        val slice = DoubleArray(data.size) { data[it][j] }
        val topColumn = topk1d(slice, size)
        for (i in topColumn.indices) result[i][j] = topColumn[i]
      }
      return result
    }
    1 -> {
      return Array(data.size) { topk1d(data[it], size) }
    }
    else -> {
      throw IllegalArgumentException("Index should be 0 or 1")
    }
  }
}

class LapTimer {
  data class Lap(val name: String, val timeMillis: Long)

  var startTime: Long = System.currentTimeMillis()
  var lastLap: Long = startTime
  val laps: MutableList<Lap> = arrayListOf()
  private var _endTime: Long? = null

  val endTime: Long
    get() = _endTime ?: throw IllegalStateException("Timer hasn't been stopped yet")

  fun lap(name: String) {
    val newLap = System.currentTimeMillis()
    laps.add(Lap(name, newLap - lastLap))
    lastLap = newLap
  }

  fun end() {
    _endTime = System.currentTimeMillis()
  }
}
