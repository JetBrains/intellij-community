package com.intellij.grazie.text

import com.intellij.grazie.spellcheck.TypoProblem
import com.intellij.grazie.style.TextLevelFix
import com.intellij.grazie.utils.HighlightingUtil.checkedDomains
import com.intellij.grazie.utils.getAllProblems
import com.intellij.grazie.utils.getGrazieTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Experimental
object ProofreadingService {
  /**
   * Returns all proofreading problems covering the specified range in the file.
   *
   * This method first checks the cached problems. If cache does not cover the range,
   * it extracts text contents from the file and performs checking.
   *
   * @param file the PSI file to check
   * @param range the text ranges to check. If empty, then returns all found problems
   * @return problems found in the specified range
   */
  @JvmStatic
  internal fun covering(file: PsiFile, range: TextRange): List<TextProblem> {
    return getAllProblems(file, checkedDomains())
      .filter { problem -> range.isEmpty || problem.intersects(range) || problem.text.rangesInFile.any { it.intersects(range) } }
      .filter { it.maybeHasSuggestions() }
  }

  /**
   * Checks whether the specified range is covered by cached proofreading results.
   *
   * This method checks both regular problem ranges and text-level problem ranges.
   *
   * @param file the PSI file to check
   * @param ranges the text ranges to verify
   * @return `true` if cache covers the range, `false` otherwise
   */
  @JvmStatic
  internal fun covers(file: PsiFile, ranges: List<TextRange>): Boolean = file.getRanges().covers(ranges)

  @JvmStatic
  internal fun PsiFile.registerProblems(problems: List<TextProblem>) {
    val problemsWithSuggestions = problems.filter { it.maybeHasSuggestions() }
    if (problemsWithSuggestions.isEmpty()) return
    this.getRanges().addAll(computeRanges(problemsWithSuggestions))
  }

  private fun computeRanges(problems: List<TextProblem>): List<TextRange> =
    problems.flatMap { getProblemTextRanges(it) }

  private fun PsiFile.getRanges(): MutableSet<TextRange> =
    CachedValuesManager.getCachedValue(this) {
      CachedValueProvider.Result.create(ConcurrentHashMap.newKeySet(), getGrazieTracker(this))
    }

  @JvmStatic
  internal fun TextProblem.hasSuggestions(): Boolean =
    this.suggestions.isNotEmpty() || this.customFixes.filterIsInstance<TextLevelFix>().flatMap { it.changes }.isNotEmpty()

  // if a typo's suggestion is to be calculated locally, let's hope there will be suggestion
  private fun TextProblem.maybeHasSuggestions(): Boolean =
    this is TypoProblem && !this.isCloud || hasSuggestions()

  private fun getProblemTextRanges(problem: TextProblem) = problem.highlightRanges.map { problem.text.textRangeToFile(it) }
  private fun TextProblem.intersects(range: TextRange) = getProblemTextRanges(this)
    .any { problemRange -> problemRange.intersects(range) }

  private fun Set<TextRange>.covers(ranges: List<TextRange>): Boolean =
    this.any { range -> ranges.any { range.intersects(it) } }
}