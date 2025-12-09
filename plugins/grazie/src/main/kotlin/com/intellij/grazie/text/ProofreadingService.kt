package com.intellij.grazie.text

import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.spellcheck.SpellingCheckerRunner
import com.intellij.grazie.spellcheck.TypoProblem
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.text.TextRangeUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class ProofreadingService(private val root: PsiElement) {

  fun getProblems(): ProofreadingProblems {
    val texts = findAllUniqueTexts(GrazieInspection.checkedDomains())
    if (GrazieInspection.skipCheckingTooLargeTexts(texts)) return ProofreadingProblems(emptyList())
    val textProblems = texts.flatMap { CheckerRunner(it).run() }
    val typos = findAllTextsExactlyAt().flatMap { SpellingCheckerRunner(it).run() }
    return ProofreadingProblems(textProblems + typos)
  }

  private fun findAllUniqueTexts(domains: Set<TextDomain>): Set<TextContent> {
    return findAllTexts { TextExtractor.findUniqueTextsAt(it, domains) }
  }

  private fun findAllTextsExactlyAt(): Set<TextContent> {
    return findAllTexts { TextExtractor.findTextsExactlyAt(it, TextDomain.ALL) }
  }

  private fun findAllTexts(extractor: (PsiElement) -> List<TextContent>): Set<TextContent> {
    val allContents = HashSet<TextContent>()
    for (element in SyntaxTraverser.psiTraverser(root)) {
      if (element is PsiWhiteSpace) continue
      allContents.addAll(extractor(element))
    }
    return allContents
  }
}


data class ProofreadingProblems(val problems: List<TextProblem>) {
  // Replace with `ProblemAggregator` with the next platform-update
  fun filterOutDuplicatedTypos(): ProofreadingProblems {
    val typos = problems.filterIsInstance<TypoProblem>()
    val typoRanges = typos.map { typo -> typo.text.textRangeToFile(typo.range) }

    val filteredTextProblems = problems.asSequence()
      .map { problem -> problem to problem.highlightRanges.map { problem.text.textRangeToFile(it) } }
      .filter { (problem, ranges) -> problem is TypoProblem || typoRanges.none { ranges.any { range -> range == it } } }
      .map { it.first }
      .toList()

    return ProofreadingProblems(filteredTextProblems)
  }

  val isEmpty: Boolean
    get() = this.problems.isEmpty()

  val textRanges: List<TextRange>
    get() {
      val ranges = problems.flatMap { it.text.rangesInFile }
        .sortedBy { it.startOffset }.distinct()
      return TextRangeUtil.mergeRanges(ranges)
    }
}