package com.intellij.cce.evaluable.conflictResolution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.absoluteValue
import kotlin.math.sign

class MatchSequenceTest {

  @Test
  fun `test complement`() {
    assertEquals(listOf(0..9), complement(10))
    assertEquals(listOf<IntRange>(), complement(10, 0..9))
    assertEquals(listOf(5..9), complement(10, 0..4))
    assertEquals(listOf(0..4), complement(10, 5..9))
    assertEquals(listOf(2..3, 6..7), complement(10, 0..1, 4..5, 8..9))
    assertEquals(listOf(0..1, 4..5, 8..9), complement(10, 2..3, 6..7))
  }

  @Test
  fun `test intersectAlignments`() {
    assertEquals(listOf<IntRange>(), intersect(listOf(), listOf()))
    assertEquals(listOf(0..9), intersect(listOf(0..9), listOf(0..9)))
    assertEquals(listOf<IntRange>(), intersect(listOf(0..9), listOf()))
    assertEquals(listOf(0..2), intersect(listOf(0..9), listOf(0..2)))
    assertEquals(listOf(3..6), intersect(listOf(0..9), listOf(3..6)))
    assertEquals(listOf(7..9), intersect(listOf(0..9), listOf(7..9)))
    assertEquals(listOf(1..2, 7..8), intersect(listOf(0..2, 7..9), listOf(1..8)))
    assertEquals(listOf(1..2, 7..8), intersect(listOf(0..2, 7..9), listOf(1..3, 6..8)))
  }

  private fun complement(size: Int, vararg ranges: IntRange): List<IntRange> {
    val offset = 10
    val scale = 2

    val sequence = MatchSequence(
      mapOf(
        TextLabel.BASE to ranges.toList(),
        TextLabel.PARENT_1 to ranges.map { (it.first + offset)..(it.last + offset) },
        TextLabel.PARENT_2 to ranges.map { (it.first * scale)..(it.last * scale) }
      ),
      mapOf(
        TextLabel.BASE to CharacterSplitText("a".repeat(size)),
        TextLabel.PARENT_1 to CharacterSplitText("b".repeat(size + offset)),
        TextLabel.PARENT_2 to CharacterSplitText("c".repeat(size * scale))
      )
    )

    val complement = sequence.complement()

    val baseRanges = complement.ranges(TextLabel.BASE)

    assertEquals(baseRanges, complement.ranges(TextLabel.PARENT_1).map { maxOf(0, it.first - offset)..(it.last - offset) })
    assertEquals(baseRanges, complement.ranges(TextLabel.PARENT_2).map {
      val start = it.first.floorDiv(scale) + it.first.rem(scale).sign.absoluteValue // round up division
      start..(if (it.last < it.first) start - 1 else it.last / scale)
    })

    return baseRanges
      .filterNot { it.isEmpty() } // because of offset, there can appear unexpected empty intervals
  }

  private fun intersect(ranges1: List<IntRange>, ranges2: List<IntRange>): List<IntRange> {
    val offset1 = 10
    val offset2 = 100

    fun size(ranges: List<IntRange>) = ranges.lastOrNull()?.last?.plus(1) ?: 0

    val baseSize = maxOf(size(ranges1), size(ranges2))
    val sequence1 = MatchSequence(
      mapOf(
        TextLabel.BASE to ranges1,
        TextLabel.PARENT_1 to ranges1.map { (it.first + offset1)..(it.last + offset1) },
      ),
      mapOf(
        TextLabel.BASE to CharacterSplitText("a".repeat(baseSize)),
        TextLabel.PARENT_1 to CharacterSplitText("b".repeat(baseSize + offset1)),
      )
    )
    val sequence2 = MatchSequence(
      mapOf(
        TextLabel.BASE to ranges2,
        TextLabel.PARENT_2 to ranges2.map { (it.first + offset2)..(it.last + offset2) },
      ),
      mapOf(
        TextLabel.BASE to CharacterSplitText("a".repeat(baseSize)),
        TextLabel.PARENT_2 to CharacterSplitText("b".repeat(baseSize + offset2)),
      )
    )

    val intersection = sequence1.intersectAlignments(sequence2)

    intersection.requireAlignment()

    val baseRanges = intersection.ranges(TextLabel.BASE)

    assertEquals(baseRanges, intersection.ranges(TextLabel.PARENT_1).map { maxOf(0, it.first - offset1)..(it.last - offset1) })
    assertEquals(baseRanges, intersection.ranges(TextLabel.PARENT_2).map { maxOf(0, it.first - offset2)..(it.last - offset2) })

    return baseRanges
  }

  private fun <T> MatchSequence<T>.ranges(label: TextLabel) = (0 until size).map { range(label, it) }
}