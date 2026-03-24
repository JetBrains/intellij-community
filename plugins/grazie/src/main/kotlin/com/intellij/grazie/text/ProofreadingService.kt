package com.intellij.grazie.text

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.spellcheck.TypoProblem
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.spellchecker.engine.DictionaryModificationTracker
import com.intellij.util.ConcurrencyUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Experimental
class ProofreadingService {
  companion object {
    private val key = Key<Ranges>("grazie.text.level.problem.ranges.in.files")

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
    fun covering(file: PsiFile, range: TextRange): List<TextProblem> {
      val texts = HighlightingUtil.getCheckedFileTexts(file.viewProvider)
        .filter { text -> range.isEmpty || text.rangesInFile.any { it.intersects(range) } }
      if (GrazieInspection.skipCheckingTooLargeTexts(texts)) return emptyList()
      val problems = texts.flatMap { text ->
        CheckerRunner(text).run()
          .filter { range.isEmpty || it.intersects(range) }
          .filter { it.hasSuggestions() }
      }
      return problems + TreeRuleChecker.checkTextLevelProblems(file)
        .filter { problem -> range.isEmpty || problem.intersects(range) || problem.text.rangesInFile.any { it.intersects(range) } }
        .filter { it.hasSuggestions() }
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
    fun covers(file: PsiFile, ranges: List<TextRange>): Boolean = file.getRangesCache().covers(ranges)

    @JvmStatic
    @ApiStatus.Internal
    internal fun PsiFile.registerProblems(problems: List<TextProblem>) {
      val problemsWithSuggestions = problems.filter { it.hasSuggestions() }
      if (problemsWithSuggestions.isEmpty()) return
      this.getRangesCache().ranges.addAll(computeRanges(problemsWithSuggestions))
    }

    private fun computeRanges(problems: List<TextProblem>): List<TextRange> =
      problems.flatMap { getProblemTextRanges(it) }

    private fun PsiFile.getRangesCache(): Ranges {
      val cache = this.getUserData(key)
      val stamp = getStamp(this)
      if (cache != null && cache.configStamp == stamp) {
        return cache
      } else {
        return ConcurrencyUtil.computeIfAbsent(this, key) { Ranges(stamp, ConcurrentHashMap.newKeySet()) }
      }
    }

    // if a typo's suggestion is to be calculated locally, let's hope there will be suggestion
    private fun TextProblem.hasSuggestions(): Boolean =
      this is TypoProblem && !this.isCloud || this.suggestions.isNotEmpty() || this.customFixes.isNotEmpty()

    private fun getStamp(file: PsiFile): Long =
      service<GrazieConfig>().modificationCount +
      DictionaryModificationTracker.getInstance(file.project).modificationCount +
      file.modificationStamp +
      PsiModificationTracker.getInstance(file.project).modificationCount

    private fun getProblemTextRanges(problem: TextProblem) = problem.highlightRanges.map { problem.text.textRangeToFile(it) }
    private fun TextProblem.intersects(range: TextRange) = getProblemTextRanges(this)
      .any { problemRange -> problemRange.intersects(range) }

    private data class Ranges(val configStamp: Long, val ranges: MutableSet<TextRange>) {
      fun covers(ranges: List<TextRange>): Boolean =
        this.ranges.any { range -> ranges.any { range.intersects(it) } }
    }
  }
}