package com.intellij.grazie.text

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.spellchecker.engine.DictionaryModificationTracker
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.text.TextRangeUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Experimental
class ProofreadingService {
  companion object {
    private val rangesKey = Key<Ranges>("grazie.text.problem.ranges.in.files")
    private val textLevelRangesKey = Key<Ranges>("grazie.text.level.problem.ranges.in.files")

    /**
     * Returns all proofreading problems covering the specified range in the file.
     * 
     * This method first checks the cached problems. If cache does not cover the range,
     * it extracts text contents from the file and performs checking.
     *
     * @param file the PSI file to check
     * @param range the text ranges to check; if empty, an empty list of problems is returned
     * @return [ProofreadingProblems] containing all problems found in the specified range
     */
    @JvmStatic
    fun covering(file: PsiFile, range: TextRange): ProofreadingProblems {
      val texts = TextExtractor.findAllTextContents(file.viewProvider, GrazieInspection.checkedDomains())
        .filter { text -> range.isEmpty || text.rangesInFile.any { it.intersects(range) } }
      if (GrazieInspection.skipCheckingTooLargeTexts(texts)) return ProofreadingProblems(emptyList())
      val problems = texts.flatMap { text ->
        CheckerRunner(text).run().filter { range.isEmpty || it.intersects(range) }
      }
      return ProofreadingProblems(problems + TreeRuleChecker.checkTextLevelProblems(file))
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
    fun covers(file: PsiFile, ranges: List<TextRange>): Boolean =
      getRangesCache(file, rangesKey, getConfigStamp(file)).covers(ranges) ||
      getRangesCache(file, textLevelRangesKey, getProjectConfigStamp(file)).covers(ranges)

    @JvmStatic
    @ApiStatus.Internal
    internal fun TextContent.registerProblems(problems: List<TextProblem>) {
      val file = this.containingFile
      val smartPointerManager = SmartPointerManager.getInstance(file.project)
      getRangesCache(file, rangesKey, getConfigStamp(file))
        .ranges.addAll(computeRanges(file, problems, smartPointerManager))
    }

    @JvmStatic
    @ApiStatus.Internal
    internal fun PsiFile.registerProblems(problems: List<TextProblem>) {
      val smartPointerManager = SmartPointerManager.getInstance(project)
      getRangesCache(this, textLevelRangesKey, getProjectConfigStamp(this))
        .ranges.addAll(computeRanges(this, problems, smartPointerManager))
    }

    private fun computeRanges(file: PsiFile, problems: List<TextProblem>, manager: SmartPointerManager): List<SmartPsiFileRange> =
      problems.flatMap { getProblemTextRanges(it) }
        .map { manager.createSmartPsiFileRangePointer(file, it) }

    private fun getRangesCache(file: PsiFile, key: Key<Ranges>, stamp: Long): Ranges {
      val cache = file.getUserData(key)
      if (cache != null && cache.configStamp == stamp) {
        return cache
      } else {
        return ConcurrencyUtil.computeIfAbsent(file, key) { Ranges(stamp, ConcurrentHashMap.newKeySet()) }
      }
    }

    private fun getProjectConfigStamp(file: PsiFile) = PsiModificationTracker.getInstance(file.project).modificationCount
    private fun getConfigStamp(file: PsiFile): Long =
      service<GrazieConfig>().modificationCount + DictionaryModificationTracker.getInstance(file.project).modificationCount + file.modificationStamp

    private fun getProblemTextRanges(problem: TextProblem) = problem.highlightRanges.map { problem.text.textRangeToFile(it) }
    private fun TextProblem.intersects(range: TextRange) = getProblemTextRanges(this)
      .any { problemRange -> problemRange.intersects(range) }

    private data class Ranges(val configStamp: Long, val ranges: MutableSet<SmartPsiFileRange>) {
      fun covers(ranges: List<TextRange>): Boolean {
        if (this.ranges.isEmpty()) return false
        return this.ranges.mapNotNull { it.range }
          .map { TextRange(it.startOffset, it.endOffset) }
          .any { range -> ranges.any { range.intersects(it) } }
      }
    }
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