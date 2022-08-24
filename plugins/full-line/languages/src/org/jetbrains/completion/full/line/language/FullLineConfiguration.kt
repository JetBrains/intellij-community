package org.jetbrains.completion.full.line.language

import org.jetbrains.completion.full.line.FullLineCompletionMode
import org.jetbrains.completion.full.line.ProposalTransformer
import org.jetbrains.completion.full.line.ProposalsFilter
import org.jetbrains.completion.full.line.TextProposalsAnalyzer

interface FullLineConfiguration {
  val mode: FullLineCompletionMode

  val filters: List<ProposalsFilter>
  val transformer: ProposalTransformer
  val analyzers: List<TextProposalsAnalyzer>

  object Line : FullLineConfiguration {
    override val mode: FullLineCompletionMode = FullLineCompletionMode.FULL_LINE
    override val filters: List<ProposalsFilter>
      get() = emptyList()
    override val transformer: ProposalTransformer = ProposalTransformer.identity()
    override val analyzers: List<TextProposalsAnalyzer>
      get() = emptyList()
  }

  companion object {
    fun oneToken(supporter: FullLineLanguageSupporter): FullLineConfiguration {
      return object : FullLineConfiguration {
        override val mode: FullLineCompletionMode = FullLineCompletionMode.ONE_TOKEN
        override val filters: List<ProposalsFilter>
          get() = emptyList()
        override val transformer: ProposalTransformer
          get() = ProposalTransformer.firstToken(supporter)
        override val analyzers: List<TextProposalsAnalyzer>
          get() = emptyList()
      }
    }
  }
}
