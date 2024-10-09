package com.intellij.cce.evaluable.conflictResolution

interface SplitText<T> {
  val text: String

  val size: Int

  operator fun get(index: Int): T

  fun substring(range: IntRange): String
  fun charOffset(index: Int): Int
}

data class LineSplitText(override val text: String) : SplitText<String> {
  private val lines = text.lines()
  private val offsets = lines.scanIndexed(0) { i, acc, line -> acc + line.length + (if (i == lines.lastIndex) 0 else 1) }

  override val size: Int get() = lines.size

  override fun get(index: Int): String = lines[index]
  override fun substring(range: IntRange): String = lines.subList(range.first, range.last + 1).joinToString("\n")
  override fun charOffset(index: Int): Int = offsets[index]
}

data class CharacterSplitText(override val text: String) : SplitText<Char> {
  override val size: Int = text.length

  override fun get(index: Int): Char = text[index]
  override fun substring(range: IntRange): String = text.substring(range)
  override fun charOffset(index: Int): Int = index
}

fun <T> calculateEditDistance(left: SplitText<T>, right: SplitText<T>): Array<IntArray> {
  val dp = Array(left.size + 1) { IntArray(right.size + 1) }

  for (i in 0..left.size) {
    for (j in 0..right.size) {
      dp[i][j] = when {
        i == 0 -> j
        j == 0 -> i
        else -> minOf(
          dp[i - 1][j] + 1,
          dp[i][j - 1] + 1,
          dp[i - 1][j - 1] + (if (left[i - 1] == right[j - 1]) 0 else 2)
        )
      }
    }
  }

  return dp
}

fun retrieveAlignment(distances: Array<IntArray>): Pair<List<IntRange>, List<IntRange>> {
  val leftRanges = mutableListOf<IntRange>()
  val rightRanges = mutableListOf<IntRange>()

  var i = distances.size - 1
  var j = distances.first().size - 1
  var matchedLength = 0

  fun commitSection() {
    if (matchedLength > 0) {
      leftRanges.add(i until i + matchedLength)
      rightRanges.add(j until j + matchedLength)
      matchedLength = 0
    }
  }

  while (i > 0 && j > 0) {
    when {
      distances[i - 1][j - 1] <= distances[i - 1][j] && distances[i - 1][j - 1] <= distances[i][j - 1] -> {
        if (distances[i - 1][j - 1] == distances[i][j]) {
          matchedLength += 1
        }
        else {
          commitSection()
        }

        i -= 1
        j -= 1
      }
      distances[i - 1][j] <= distances[i][j - 1] -> {
        commitSection()
        i -= 1
      }
      else -> {
        commitSection()
        j -= 1
      }
    }
  }
  commitSection()

  return Pair(leftRanges.reversed(), rightRanges.reversed())
}