package org.jetbrains.completion.full.line.platform.weigher

import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import org.jetbrains.completion.full.line.FullLineProposal
import org.jetbrains.completion.full.line.ReferenceCorrectness
import org.jetbrains.completion.full.line.platform.FullLineLookupElement

abstract class FullLineWeigher(id: String) : LookupElementWeigher("full-line-$id", true, false) {
  override fun weigh(element: LookupElement): Comparable<Nothing>? {
    return if (element is FullLineLookupElement) weighFullLineLookup(element) else null
  }

  abstract fun weighFullLineLookup(element: FullLineLookupElement): Comparable<Nothing>?

  companion object {
    private val INSTANCES = arrayOf(
      FullLineTabWeigher(),
      FullLineSyntaxCorrectnessWeigher(),
      FullLineReferenceCorrectnessWeigher(),
      FullLineScoreWeigher()
    )

    fun customizeSorter(sorter: CompletionSorter): CompletionSorter {
      return sorter.weighAfter("priority", *INSTANCES)
    }
  }
}

/**
 * Sorting full line lookups by priority:
 * - Selected with `Tab`
 * - Syntax correctness
 * - Reference correctness
 */
class FullLineTabWeigher : FullLineWeigher("tab") {
  override fun weighFullLineLookup(element: FullLineLookupElement): Comparable<Boolean> {
    return element.selectedByTab
  }
}

class FullLineSyntaxCorrectnessWeigher : FullLineWeigher("syntax") {
  override fun weighFullLineLookup(element: FullLineLookupElement): Comparable<FullLineProposal.BasicSyntaxCorrectness> {
    return element.proposal.isSyntaxCorrect
  }
}

class FullLineReferenceCorrectnessWeigher : FullLineWeigher("ref") {
  override fun weighFullLineLookup(element: FullLineLookupElement): Comparable<ReferenceCorrectness> {
    return element.proposal.refCorrectness
  }
}

class FullLineScoreWeigher : FullLineWeigher("score") {
  override fun weighFullLineLookup(element: FullLineLookupElement): Comparable<Double> {
    return element.proposal.score
  }
}
