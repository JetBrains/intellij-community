package org.jetbrains.completion.full.line.local.generation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

class UtilsTest {
  @Test
  fun testTopk1d() {
    val list = doubleArrayOf(0.1, 0.2, 0.9, 0.3, 0.4, 0.0, 0.1, 0.5)
    val expected = intArrayOf(2, 7, 4)
    assertArrayEquals(topk1d(list, 3), expected)
  }

  @Test
  fun testTopk1dSameSize() {
    val list = doubleArrayOf(0.1, 0.9, 0.3)
    val expected = intArrayOf(1, 2, 0)
    assertArrayEquals(topk1d(list, 3), expected)
  }

  @Test
  fun testTopk1dLessSize() {
    val list = doubleArrayOf(0.1, 0.9)
    val expected = intArrayOf(1, 0)
    assertArrayEquals(topk1d(list, 3), expected)
  }

  @Test
  fun testTopk1dCorrectness() {
    val list = DoubleArray(10000) { Random.nextDouble() }
    val k = 10
    val expected = list
      .mapIndexed { i, v -> Pair(i, v) }
      .sortedByDescending { it.second }
      .take(k)
      .map { it.first }
      .toIntArray()
    assertArrayEquals(topk1d(list, k), expected)
  }

  @Test
  fun testTopk2d() {
    val list1 = doubleArrayOf(0.1, 0.2, 0.9, 0.3, 0.4, 0.0, 0.1, 0.5)
    val list2 = doubleArrayOf(0.6, 0.8, 0.2, 0.1, 0.4, 0.7, 0.3, 0.0)
    val list = arrayOf(list1, list2)
    val expected = arrayOf(intArrayOf(2, 7, 4), intArrayOf(1, 5, 0))
    assertArrayEquals(topk2d(list, 3, dim = 1), expected)

    val column = arrayOf(
      doubleArrayOf(0.9, 0.3),
      doubleArrayOf(0.2, 0.2),
      doubleArrayOf(0.1, 0.7),
      doubleArrayOf(0.5, 0.4)
    )
    val target = arrayOf(
      intArrayOf(0, 2),
      intArrayOf(3, 3),
      intArrayOf(1, 0)
    )
    assertArrayEquals(topk2d(column, 3, dim = 0), target)
  }

  @Test
  fun testLogSoftmaxManual() {
    val logits = Array(2) { i -> DoubleArray(3) { j -> (i * 3 + j).toDouble() } }
    val pyTorchOut = arrayOf(doubleArrayOf(-2.4076, -1.4076, -0.4076), doubleArrayOf(-2.4076, -1.4076, -0.4076))

    logSoftmax(logits, fast = false)
    assert(isClose(logits, pyTorchOut, atol=1e-5))  // 1e-5 because printed values from PyTorch have such precision
  }

  @Test
  fun testLogSoftmaxRandom() {
    val logits = Array(6) { DoubleArray(16384) { Random.nextDouble() } }

    logSoftmax(logits, fast = false)
    assert(logits.all { arr -> arr.all { it.isFinite() } || isClose(arr.sumOf { exp(it) }, 1.0) })
  }

  @Test
  fun testLogSoftmaxStabilitySimple() {
    // https://discuss.pytorch.org/t/justification-for-logsoftmax-being-better-than-log-softmax/140130/3
    val alpha = 100.0
    val logits = arrayOf(doubleArrayOf (-alpha, 0.0, alpha))
    val logitsFast = arrayOf(doubleArrayOf (-alpha, 0.0, alpha))

    logSoftmax(logits, fast = false)
    logSoftmax(logits, fast = true)
    assert(logits[0].all { it.isFinite() })
    assert(logitsFast[0].all { it.isFinite() })  // fails at alpha=1000
  }

  @Test
  fun testLogSoftmaxStabilityHard() {
    // https://discuss.pytorch.org/t/justification-for-logsoftmax-being-better-than-log-softmax/140130/3
    val alpha = 1000.0
    val logits = arrayOf(doubleArrayOf (-alpha, 0.0, alpha))

    logSoftmax(logits, fast = false)
    assert(logits[0].all { it.isFinite() })
  }

  @Test
  fun testLogSoftmaxFastImplCloseness() {
    val logitsA = Array(6) { DoubleArray(16384) { Random.nextDouble() } }
    val logitsB = Array(6) { i -> logitsA[i].copyOf() }

    logSoftmax(logitsA, fast = false)
    logSoftmax(logitsB, fast = true)
    assert(isClose(logitsA, logitsB, rtol=1e-4, atol=1e-7)) // minimum tolerance when the test is passing
  }

  private fun isClose(a: Array<DoubleArray>, b: Array<DoubleArray>, rtol: Double = 1e-5, atol: Double = 1e-8): Boolean {
    return a.zip(b).all { (arrA, arrB) ->
      arrA.zip(arrB).all { (elA, elB) -> isClose(elA, elB, rtol, atol) }
    }
  }

  private fun isClose(a: Double, b: Double, rtol: Double = 1e-5, atol: Double = 1e-8): Boolean {
    // https://pytorch.org/docs/stable/generated/torch.isclose.html
    return abs(a - b) <= atol + rtol * abs(b)
  }
}
