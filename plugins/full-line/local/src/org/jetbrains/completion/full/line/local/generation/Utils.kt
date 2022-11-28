package org.jetbrains.completion.full.line.local.generation

import java.util.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

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

private data class IndexToValue(val index: Int, val value: Double) : Comparable<IndexToValue> {
  override fun compareTo(other: IndexToValue): Int = -compareValues(this.value, other.value)
}

internal fun topk1d(data: DoubleArray, size: Int): IntArray {
  val newData = data.mapIndexed { i, v -> IndexToValue(i, v) }.filter { it.value != Double.NEGATIVE_INFINITY }.toMutableList()
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
