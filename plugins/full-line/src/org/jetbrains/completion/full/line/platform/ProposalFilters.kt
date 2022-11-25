package org.jetbrains.completion.full.line.platform

import org.jetbrains.completion.full.line.AnalyzedFullLineProposal
import org.jetbrains.completion.full.line.ProposalsFilter
import org.jetbrains.completion.full.line.RawFullLineProposal
import kotlin.math.min


object RedCodeFilter : ProposalsFilter.Adapter("red code") {
  override fun checkAnalyzedFullLine(proposal: AnalyzedFullLineProposal): Boolean {
    return proposal.refCorrectness.isCorrect()
  }
}

class SameAsPrefixFilter(private val prefix: String) : ProposalsFilter.Adapter("same as prefix") {
  override fun checkRawFullLine(proposal: RawFullLineProposal): Boolean {
    return proposal.suggestion != prefix
  }
}

object ProhibitedWordsFilter : ProposalsFilter.Adapter("prohibited words") {
  private val prohibited = listOf(
    "龖", "#", "print ", "BOS", "<PAD>", "<EOS>", "<UNK>"
  )

  override fun checkRawFullLine(proposal: RawFullLineProposal): Boolean {
    return prohibited.none { proposal.suggestion.contains(it) }
  }
}

object SemanticFilter : ProposalsFilter.Adapter("doesn't make sense") {
  override fun checkRawFullLine(proposal: RawFullLineProposal): Boolean {
    return proposal.suggestion.find { it.isJavaIdentifierPart() || it.isDigit() } != null
  }
}

object EmptyStringFilter : ProposalsFilter.Adapter("empty") {
  override fun checkRawFullLine(proposal: RawFullLineProposal): Boolean {
    return proposal.suggestion.isNotEmpty()
  }
}

object ScoreFilter : ProposalsFilter.Adapter("low score") {
  private const val scoreThreshold: Double = 0.1

  override fun checkRawFullLine(proposal: RawFullLineProposal): Boolean {
    return proposal.score > scoreThreshold
  }
}

object ASCIIFilter : ProposalsFilter.Adapter("non-ACII symbols") {
  override fun checkRawFullLine(proposal: RawFullLineProposal): Boolean {
    return proposal.suggestion.toCharArray().all { it in ' '..'~' || it == '⇥' || it == '⇤' }
  }
}

// Do not find repetition in sub-strings, only if whole string is repetition
object RepetitionFilter : ProposalsFilter.Adapter("repetition") {
  private const val repetition = 3

  override fun checkRawFullLine(proposal: RawFullLineProposal): Boolean {
    val zArr = zFunction(proposal.suggestion)
    return !findRepetition(zArr)
  }

  private fun findRepetition(arr: IntArray): Boolean {
    for ((i, z) in arr.withIndex()) {
      if ((i + z) == arr.size && arr.size % i == 0 && arr.size / i >= repetition) {
        return true
      }
    }
    return false
  }

  private fun zFunction(line: String): IntArray {
    val z = IntArray(line.length)

    var l = 0
    var r = 0
    for (i in 1..(line.length / 2)) {
      if (i <= r) {
        z[i] = min(r - i + 1, z[i - l])
      }
      while (i + z[i] < line.length && line[z[i]] == line[i + z[i]]) {
        ++z[i]
      }
      if (i + z[i] - 1 > r) {
        l = i
        r = i + z[i] - 1
      }
    }
    return z
  }
}
