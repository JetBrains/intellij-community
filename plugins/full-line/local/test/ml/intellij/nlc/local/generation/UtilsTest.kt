package ml.intellij.nlc.local.generation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class UtilsTest {
  @Test
  @Tag("heavy")
  fun testTopk1d() {
    val list = doubleArrayOf(0.1, 0.2, 0.9, 0.3, 0.4, 0.0, 0.1, 0.5)
    val expected = intArrayOf(2, 7, 4)
    assertArrayEquals(topk1d(list, 3), expected)
  }

  @Test
  @Tag("heavy")
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
}
