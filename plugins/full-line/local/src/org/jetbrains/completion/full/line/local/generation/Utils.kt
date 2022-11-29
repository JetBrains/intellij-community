package org.jetbrains.completion.full.line.local.generation

import java.util.*
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.min

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

internal fun logSoftmax(logits: Array<DoubleArray>, fast: Boolean = false) {
  // https://github.com/pytorch/pytorch/blob/420b37f3c67950ed93cd8aa7a12e673fcfc5567b/aten/src/ATen/native/SoftMax.cpp#L41
  val stableLogSumExps = DoubleArray(logits.size) { rowId ->
      val maxLogit = logits[rowId].max()
      when (fast) {
        true -> maxLogit + ln(logits[rowId].sumOf { fastExp(it - maxLogit) })
        false -> maxLogit + ln(logits[rowId].sumOf { exp(it - maxLogit) })
    }
  }
  for (rowId in logits.indices) {
    val logitsRow = logits[rowId]
    val logSumExp = stableLogSumExps[rowId]
    for (colId in logitsRow.indices) {
      logitsRow[colId] -= logSumExp
    }
  }
}

private fun fastExp(value: Double): Double {
  val tmp = (1512775 * value + 1072632447).toLong()
  return java.lang.Double.longBitsToDouble(tmp shl 32)
}

private data class IndexToValue(val index: Int, val value: Double) : Comparable<IndexToValue> {
  override fun compareTo(other: IndexToValue): Int = -compareValues(this.value, other.value)
}

internal fun topk1d(data: DoubleArray, size: Int): IntArray {
  val newData = mutableListOf<IndexToValue>()
  for ((index, value) in data.withIndex()) {
    if (value != Double.NEGATIVE_INFINITY) {
      newData.add(IndexToValue(index, value))
    }
  }
  val queue = PriorityQueue(newData)
  return IntArray(min(size, queue.size)) { queue.poll().index }
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
