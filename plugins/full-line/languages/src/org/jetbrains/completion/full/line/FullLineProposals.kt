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

data class RawFullLineProposal(
    override val suggestion: String,
    override val score: Double,
    override val isSyntaxCorrect: FullLineProposal.BasicSyntaxCorrectness,
    var provider: String? = null,
    val cacheHitLength: Int? = null
) : FullLineProposal {

    fun withSuggestion(text: String): RawFullLineProposal {
        if (text != suggestion) {
            return RawFullLineProposal(text, score, isSyntaxCorrect, provider, cacheHitLength)
        }

        return this
    }
}

data class AnalyzedFullLineProposal(
    private val source: RawFullLineProposal,
    val suffix: String,
    val refCorrectness: ReferenceCorrectness
) : FullLineProposal by source {
    val provider: String? = source.provider
}