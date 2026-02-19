package com.intellij.cce.evaluable.conflictResolution


/**
 * Represents a sequence of matched ranges within texts.
 * Can be used to store texts alignment or conflict sequence.
 *
 * @constructor For each text range, there should be corresponding ranges in other texts (maybe empty ones).
 *              Ranges for each text should be sorted.
 *              Check the init section for range restrictions.
 */
class MatchSequence<T>(
  private val ranges: Map<TextLabel, List<IntRange>>,
  private val originalTexts: Map<TextLabel, SplitText<T>>,
) {
  init {
    require(ranges.keys == originalTexts.keys) {
      "Keys mismatch: ${ranges.keys} != ${originalTexts.keys}"
    }

    require(ranges.map { it.value.size }.distinct().size <= 1) {
      "For all texts there should be the same amount of ranges."
    }

    for ((label, labelRanges) in ranges) {
      require((labelRanges.firstOrNull()?.first ?: 0) >= 0) {
        "First range should be non-negative in $label: $labelRanges"
      }

      for (i in 0 until labelRanges.size - 1) {
        require(labelRanges[i].last < labelRanges[i + 1].first) {
          "Ranges should be sorted in $label: $labelRanges"
        }
      }

      require((labelRanges.lastOrNull()?.last ?: -1) < originalTexts.getValue(label).size) {
        "Last range shouldn't be greater then text size in $label: $labelRanges"
      }
    }
  }

  val keys get() = ranges.keys

  val size: Int = keys.firstOrNull()?.let { ranges[it]!!.size } ?: 0

  fun range(label: TextLabel, index: Int) = ranges.getValue(label)[index]

  fun text(label: TextLabel) = originalTexts.getValue(label).text
  fun text(label: TextLabel, index: Int): String = originalTexts.getValue(label).substring(range(label, index))

  fun charOffset(label: TextLabel, index: Int): Int = originalTexts.getValue(label).charOffset(range(label, index).first)

  fun filter(indexes: Iterable<Int>): MatchSequence<T> {
    return MatchSequence(
      ranges.mapValues { it.value.filterIndexed { index, _ -> indexes.contains(index) } },
      originalTexts
    )
  }

  fun requireAlignment() {
    for (index in 0 until size) {
      val indexRanges = ranges.map { it.key to it.value[index] }.toMap()
      require(indexRanges.map { it.value.last - it.value.first }.distinct().size == 1) {
        "There are ranges of different sizes in mapping $index: $indexRanges\n$ranges"
      }
    }
  }

  /**
   * Generates a sequence of text ranges that are not covered by the original match ranges.
   *
   * This method computes the complement of the original match ranges, effectively identifying all
   * text ranges that do not match.
   */
  fun complement(): MatchSequence<T> {
    if (ranges.isEmpty() || size == 0) {
      return MatchSequence(originalTexts.map { (key, text) -> key to listOf(0 until text.size) }.toMap(), originalTexts)
    }

    val result = ranges.map { it.key to mutableListOf<IntRange>() }.toMap()

    if (ranges.any { it.value.first().first > 0 }) {
      ranges.forEach { (key, keyRanges) ->
        result.getValue(key) += (0 until keyRanges.first().first)
      }
    }

    for (i in 0 until size - 1) {
      ranges.forEach { (key, keyRanges) ->
        val prev = keyRanges[i]
        val next = keyRanges[i + 1]

        result.getValue(key) += (prev.last + 1 until next.first)
      }
    }

    if (ranges.any { (key, ranges) -> ranges.last().last + 1 < originalTexts.getValue(key).size }) {
      ranges.forEach { (key, keyRange) ->
        result.getValue(key) += (keyRange.last().last + 1 until originalTexts.getValue(key).size)
      }
    }

    return MatchSequence(result, originalTexts)
  }

  /**
   * Intersects the current MatchSequence with another provided MatchSequence.
   *
   * This method requires that both sequences align properly. It computes the intersection
   * of the sequences using only one common key and ensures that the original texts for the
   * common key are identical.
   */
  fun intersectAlignments(other: MatchSequence<T>): MatchSequence<T> {
    requireAlignment()
    other.requireAlignment()

    val intersectionKeys = keys intersect other.keys
    require(intersectionKeys.size == 1) {
      "Only intersection of two sequences with one same key is supported: ${keys}, ${other.keys}"
    }

    val commonKey = intersectionKeys.first()

    require(originalTexts.getValue(commonKey) == other.originalTexts.getValue(commonKey)) {
      "Texts are different for key $commonKey"
    }

    val result = (keys union other.keys).associateWith { mutableListOf<IntRange>() }

    var pointer1 = 0
    var pointer2 = 0

    while (pointer1 < size && pointer2 < other.size) {
      val range1 = range(commonKey, pointer1)
      val range2 = other.range(commonKey, pointer2)

      val first = maxOf(range1.first, range2.first)

      if (range1.last < first) {
        pointer1 += 1
        continue
      }

      if (range2.last < first) {
        pointer2 += 1
        continue
      }

      val last = minOf(range1.last, range2.last)
      val length = last - first + 1

      val delta1 = first - range1.first
      val delta2 = first - range2.first

      ranges.forEach { (key, keyRanges) ->
        val range = keyRanges[pointer1]
        result.getValue(key) += range.first + delta1 until range.first + delta1 + length
      }

      other.ranges.filter { it.key != commonKey }.forEach { (key, keyRanges) ->
        val range = keyRanges[pointer2]
        result.getValue(key) += range.first + delta2 until range.first + delta2 + length
      }

      if (range1.last == last) {
        pointer1 += 1
      }


      if (range2.last == last) {
        pointer2 += 1
      }
    }

    return MatchSequence(result, originalTexts + other.originalTexts)
  }
}

enum class TextLabel {
  BASE,
  PARENT_1,
  PARENT_2,
  TARGET,
  RESULT;
}
