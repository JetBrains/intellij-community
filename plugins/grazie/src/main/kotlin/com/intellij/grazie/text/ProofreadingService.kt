package com.intellij.grazie.text

import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.text.TextRangeUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class ProofreadingService(private val root: PsiElement) {

  fun getProblems(): ProofreadingProblems {
    val texts = TextExtractor.findAllTextContents(root.containingFile.viewProvider, GrazieInspection.checkedDomains())
    if (GrazieInspection.skipCheckingTooLargeTexts(texts)) return ProofreadingProblems(emptyList())
    val textProblems = texts.flatMap { CheckerRunner(it).run() }
    return ProofreadingProblems(textProblems)
  }
}

data class ProofreadingProblems(val problems: List<TextProblem>) {
  val isEmpty: Boolean
    get() = this.problems.isEmpty()

  val textRanges: List<TextRange>
    get() {
      val ranges = problems.flatMap { it.text.rangesInFile }
        .sortedBy { it.startOffset }.distinct()
      return TextRangeUtil.mergeRanges(ranges)
    }
}