package org.jetbrains.completion.full.line.platform

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.completion.full.line.AnalyzedFullLineProposal
import org.jetbrains.completion.full.line.ProposalsFilter
import org.jetbrains.completion.full.line.RawFullLineProposal
import java.util.*

class SuggestionHolder {
  private val standardElements = TreeSet<String>()
  private val fullLineElements = TreeSet<String>()

  fun standardAdded(text: String) {
    standardElements.add(text)
  }

  fun fullLineAdded(text: String) {
    fullLineElements.add(text)
  }

  fun duplicateFilter(): ProposalsFilter {
    return object : ProposalsFilter.Adapter("duplicates") {
      override fun checkStandard(element: LookupElement): Boolean {
        return element.lookupString !in fullLineElements
      }

      override fun checkRawFullLine(proposal: RawFullLineProposal): Boolean {
        return checkSuggestion(proposal.suggestion)
      }

      override fun checkAnalyzedFullLine(proposal: AnalyzedFullLineProposal): Boolean {
        return checkSuggestion(proposal.suggestion)
      }

      private fun checkSuggestion(text: String): Boolean {
        return text !in standardElements && text !in fullLineElements
      }
    }
  }

}
