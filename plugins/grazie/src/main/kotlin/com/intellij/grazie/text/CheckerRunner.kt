@file:Suppress("DEPRECATION")

package com.intellij.grazie.text

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.tokenizer.Tokenizer
import ai.grazie.nlp.tokenizer.sentence.StandardSentenceTokenizer
import ai.grazie.utils.toLinkedSet
import com.intellij.codeInsight.daemon.impl.ProblemDescriptorWithReporterName
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.ide.fus.AcceptanceRateTracker
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.ide.inspection.grammar.quickfix.*
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.NaturalTextDetector.seemsNatural
import com.intellij.grazie.utils.getTextDomain
import com.intellij.grazie.utils.toProofreadingContext
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil.BombedCharSequence
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.parents
import com.intellij.psi.util.startOffset
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus

private val LOG = Logger.getInstance(CheckerRunner::class.java)

class CheckerRunner(val text: TextContent) {
  private val tokenizer
    get() = StandardSentenceTokenizer.Default

  private val sentences by lazy { tokenize(text) }

  private fun tokenize(text: TextContent): List<Tokenizer.Token> {
    val sequence = object : BombedCharSequence(text.toString()) {
      override fun checkCanceled() {
        ProgressManager.checkCanceled()
      }
    }
    val ranges = tokenizer.tokenRanges(sequence)
    return ranges.map { Tokenizer.Token(text.substring(it.start, it.endExclusive), it.start..it.endExclusive) }
  }

  fun run(): List<TextProblem> {
    if (text.isBlank() || !seemsNatural(text)) return emptyList()

    val context = text.toProofreadingContext()
    if (context.language == Language.UNKNOWN || HighlightingUtil.findInstalledLang(context.language) == null) return emptyList()
    return filter(doRun(TextChecker.allCheckers(), context))
  }

  @Suppress("unused")
  @Deprecated("This method is deprecated and does nothing. Use run() instead.")
  @ApiStatus.ScheduledForRemoval
  fun run(checkers: List<TextChecker>, consumer: (List<TextProblem>) -> Unit) {
    // No-op implementation to prevent NoSuchMethodError
  }

  /**
   * We want for the CPU-bound checkers to all happen on the same thread
   * because other threads are all needed by other inspections during highlighting.
   * But we also want for external checkers to make their network requests in parallel.
   *
   * So we split the checkers into coroutines but dispatch them on the same thread sequentially.
   * We schedule the external checkers to start as soon as possible
   * to allow them to make the requests and suspend, giving up the thread to others.
   * Then we explicitly start the non-external checkers to do their work, probably CPU-bound.
   * We periodically yield to allow the external checkers to process their network responses (if any) and possibly suspend further.
   *
   * In the end, we still collect the results in the checker registration order
   * so that problems from the first checkers can override intersecting problems from others.
   */
  private fun doRun(checkers: List<TextChecker>, context: ProofreadingContext): List<TextProblem> {
    return runBlockingCancellable {
      val deferred = checkers.map { checker ->
        when (checker) {
          is ExternalTextChecker -> async { checker.checkExternally(context) }
          else -> async(start = CoroutineStart.LAZY) { checker.check(context) }
        }
      }
      for (job in deferred) {
        yield() // let all pending external checker jobs complete what they're ready to do and possibly suspend further
        job.start()
      }
      deferred.awaitAll().flatten()
    }
  }

  private fun filter(problems: List<TextProblem>): List<TextProblem> =
    TextProblemAggregator.aggregate(text.toString(), problems.filterNot { shouldBeIgnored(it) })

  private fun shouldBeIgnored(problem: TextProblem): Boolean =
    isSuppressed(problem) ||
    hasIgnoredCategory(problem) ||
    isIgnoredByStrategies(problem) ||
    ProblemFilter.allIgnoringFilters(problem).findAny().isPresent

  fun toProblemDescriptors(problem: TextProblem, isOnTheFly: Boolean): List<ProblemDescriptor> {
    val parent = text.commonParent
    val tooltip = problem.tooltipTemplate
    val description = problem.getDescriptionTemplate(isOnTheFly)
    return fileHighlightRanges(problem).map { range ->
      val rangeInElement = range.shiftLeft(parent.startOffset)
      validateRangeInElement(parent, rangeInElement, problem)
      val grazieDescriptor = GrazieProblemDescriptor(parent, description, rangeInElement, isOnTheFly, tooltip)
      if (isOnTheFly) {
        grazieDescriptor.quickFixes = toFixes(problem, grazieDescriptor)
      }
      val shortName = if (problem.isStyleLike) GrazieInspection.STYLE_INSPECTION else GrazieInspection.GRAMMAR_INSPECTION
      val descriptor = ProblemDescriptorWithReporterName(grazieDescriptor, shortName)
      descriptor.problemGroup = ProblemGroup { shortName }
      descriptor
    }
  }

  private fun validateRangeInElement(psi: PsiElement, rangeInElement: TextRange?, problem: TextProblem) {
    if (rangeInElement != null && psi.textRange != null) {
      TextRange.assertProperRange(rangeInElement)
      val psiTextLength = psi.textRange.length
      if (rangeInElement.endOffset > psiTextLength) {
        LOG.error("Argument rangeInElement ($rangeInElement) endOffset must not exceed descriptor text range " +
                  "(${psi.textRange.startOffset}, ${psi.textRange.endOffset}) length ($psiTextLength). " +
                  "PSI language: ${psi.language.id}, TextContent.fileRanges: ${problem.text.rangesInFile}")
      }
    }
  }

  // a non-anonymous class to work around KT-48784
  private class GrazieProblemDescriptor(psi: PsiElement,
                                        @InspectionMessage descriptionTemplate: String,
                                        rangeInElement: TextRange?,
                                        onTheFly: Boolean,
                                        @NlsContexts.Tooltip private val tooltip: String
  ): ProblemDescriptorBase(
    psi, psi, descriptionTemplate, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false,
    rangeInElement, true, onTheFly, tooltip
  ) {
    var quickFixes: Array<LocalQuickFix> = LocalQuickFix.EMPTY_ARRAY

    override fun getFixes(): Array<LocalQuickFix> = quickFixes
  }

  private fun isIgnoredByStrategies(descriptor: TextProblem): Boolean {
    for (root in text.findPsiElementAt(0).parents(withSelf = true)) {
      for (strategy in LanguageGrammarChecking.allForLanguage(root.language)) {
        if (strategy.isMyContextRoot(root)) {
          val errorRange = text.textRangeToFile(highlightSpan(descriptor)).shiftLeft(root.startOffset)
          val patternRange = text.textRangeToFile(descriptor.patternRange ?: highlightSpan(descriptor)).shiftLeft(root.startOffset)
          val typoRange = errorRange.startOffset until errorRange.endOffset
          val ruleRange = patternRange.startOffset until patternRange.endOffset
          if (!strategy.isTypoAccepted(text.commonParent, strategy.getRootsChain(root), typoRange, ruleRange) ||
              !strategy.isTypoAccepted(root, typoRange, ruleRange)) {
            return true
          }
        }
      }
    }
    return false
  }

  private fun hasIgnoredCategory(problem: TextProblem): Boolean {
    val ignored = ignoredRules(problem)
    return ignored.rules.isNotEmpty() && problem.fitsGroup(ignored)
  }

  private fun ignoredRules(descriptor: TextProblem): RuleGroup {
    val leaves = descriptor.highlightRanges.asSequence()
      .flatMap { it.startOffset until it.endOffset }
      .map { text.findPsiElementAt(it) }
      .toLinkedSet()
    val ignored = LinkedHashSet<String>()
    for (leaf in leaves) {
      for (root in leaf.parents(withSelf = true)) {
        for (strategy in LanguageGrammarChecking.allForLanguage(root.language)) {
          for (child in leaf.parents(withSelf = true)) {
            val group = strategy.getIgnoredRuleGroup(root, child)
            if (group != null) ignored.addAll(group.rules)
            if (child == root) break
          }
        }
      }
    }
    return RuleGroup(ignored)
  }

  private fun isSuppressed(problem: TextProblem): Boolean {
    val sentence = findSentence(problem)
    if (defaultSuppressionPattern(problem, sentence).isSuppressed()) {
      return true
    }

    val patternRange = problem.patternRange
    val errorText = highlightSpan(problem).subSequence(text)
    return patternRange != null && sentence != null && SuppressionPattern(errorText, sentence).isSuppressed()
  }

  // used in rider
  @ApiStatus.Experimental
  fun findSentence(problem: TextProblem): String? {
    return sentences.find { sentence -> problem.highlightRanges.any { range -> range.intersectsStrict(sentence.range.first, sentence.range.last) } }?.token
  }

  fun toFixes(problem: TextProblem, descriptor: ProblemDescriptor): Array<LocalQuickFix> {
    val file = text.containingFile
    val result = arrayListOf<LocalQuickFix>()
    val spm = SmartPointerManager.getInstance(file.project)
    val underline = fileHighlightRanges(problem).map { spm.createSmartPsiFileRangePointer(file, it) }

    if (problem.suggestions.isNotEmpty()) {
      GrazieFUSCounter.typoFound(problem)
      result.addAll(GrazieReplaceTypoQuickFix.getReplacementFixes(problem, underline))
    }

    problem.customFixes.forEachIndexed { index, fix -> result.add(GrazieCustomFixWrapper(problem, fix, descriptor, index)) }

    val suppressionPattern = defaultSuppressionPattern(problem, findSentence(problem))
    result.add(object : GrazieAddExceptionQuickFix(suppressionPattern, underline) {
      override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
        GrazieFUSCounter.exceptionAdded(project, AcceptanceRateTracker(problem))
        super.applyFix(project, psiFile, editor)
      }
    })
    result.add(GrazieRuleSettingsAction(problem.rule, problem.text.getTextDomain()))
    result.add(GrazieMassApplyAction())
    result.add(GrazieEnableCloudAction())
    return result.toTypedArray()
  }

  // used in rider
  @ApiStatus.Experimental
  fun defaultSuppressionPattern(problem: TextProblem, sentenceText: String?): SuppressionPattern {
    val text = problem.text
    val patternRange = problem.patternRange
    if (patternRange != null) {
      return SuppressionPattern(patternRange.subSequence(text), null)
    }
    return SuppressionPattern(highlightSpan(problem).subSequence(text), sentenceText)
  }

  private fun highlightSpan(problem: TextProblem) =
    TextRange(problem.highlightRanges[0].startOffset, problem.highlightRanges.last().endOffset)

  companion object {
    @JvmStatic
    fun fileHighlightRanges(problem: TextProblem): List<TextRange> {
      return problem.highlightRanges.asSequence()
        .map { problem.text.textRangeToFile(it) }
        .flatMap { range -> problem.text.intersection(range) }
        .filterNot { it.isEmpty }
        .toList()
    }
  }
}