package org.jetbrains.completion.full.line

import com.intellij.codeInsight.lookup.LookupElement

// Checks if standard/FL proposal can be shown
interface ProposalsFilter {
  val description: String

  fun checkStandard(element: LookupElement): Boolean
  fun checkRawFullLine(proposal: RawFullLineProposal): Boolean
  fun checkAnalyzedFullLine(proposal: AnalyzedFullLineProposal): Boolean

  abstract class Adapter(override val description: String) : ProposalsFilter {
    override fun checkStandard(element: LookupElement): Boolean = true
    override fun checkRawFullLine(proposal: RawFullLineProposal): Boolean = true
    override fun checkAnalyzedFullLine(proposal: AnalyzedFullLineProposal): Boolean = true
  }
}
