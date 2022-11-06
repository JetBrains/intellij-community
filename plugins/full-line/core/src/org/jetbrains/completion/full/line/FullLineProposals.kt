package org.jetbrains.completion.full.line

interface FullLineProposal {
  val suggestion: String
  val score: Double
  val isSyntaxCorrect: BasicSyntaxCorrectness

  enum class BasicSyntaxCorrectness {
    UNKNOWN, INCORRECT, CORRECT;

    companion object {
      fun fromBoolean(value: Boolean?): BasicSyntaxCorrectness {
        if (value == null) return UNKNOWN
        return if (value) CORRECT else INCORRECT
      }
    }
  }
}

class RawFullLineProposal private constructor(
  override val suggestion: String,
  override val score: Double,
  override val isSyntaxCorrect: FullLineProposal.BasicSyntaxCorrectness,
  val details: Details
) : FullLineProposal {

  constructor(suggestion: String, score: Double, isSyntaxCorrect: FullLineProposal.BasicSyntaxCorrectness)
    : this(suggestion, score, isSyntaxCorrect, Details())

  fun withSuggestion(text: String): RawFullLineProposal {
    if (text != suggestion) {
      return RawFullLineProposal(text, score, isSyntaxCorrect, details.copy())
    }

    return this
  }

  data class Details(
    var provider: String? = null,
    var cacheHitLength: Int? = null,
    var inferenceTime: Int? = null,
    var checksTime: Int? = null,
  )
}

data class AnalyzedFullLineProposal(
  private val source: RawFullLineProposal,
  val suffix: String,
  val refCorrectness: ReferenceCorrectness
) : FullLineProposal by source {
  val provider: String? = source.details.provider
  val details: RawFullLineProposal.Details = source.details
}
