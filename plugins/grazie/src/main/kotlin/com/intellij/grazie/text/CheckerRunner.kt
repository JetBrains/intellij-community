@file:Suppress("DEPRECATION")

package com.intellij.grazie.text

import ai.grazie.nlp.tokenizer.Tokenizer
import ai.grazie.nlp.tokenizer.sentence.StandardSentenceTokenizer
import ai.grazie.utils.toLinkedSet
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieAddExceptionQuickFix
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieCustomFixWrapper
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieRuleSettingsAction
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.coroutineToIndicator
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
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

class CheckerRunner(val text: TextContent) {
  private val tokenizer
    get() = StandardSentenceTokenizer.Default

  private val sentences by lazy { tokenize(text) }

  private fun tokenize(text: TextContent): List<Tokenizer.Token> {
    val sequence = object: BombedCharSequence(text.toString()) {
      override fun checkCanceled() {
        ProgressManager.checkCanceled()
      }
    }
    val ranges = tokenizer.tokenRanges(sequence)
    return ranges.map { Tokenizer.Token(text.substring(it.start, it.endExclusive), it.start..it.endExclusive) }
  }

  fun run(checkers: List<TextChecker>, consumer: (TextProblem) -> Unit) {
    runBlockingCancellable {
      val deferred: List<Deferred<Collection<TextProblem>>> = checkers.map { checker ->
        when (checker) {
          is ExternalTextChecker -> async { checker.checkExternally(text) }
          else -> async(start = CoroutineStart.LAZY) { blockingContext { checker.check(text) } }
        }
      }
      launch {
        for (job in deferred) {
          yield() // allow the main coroutine to process the available results as soon as possible
          job.start()
        }
      }

      val filtered = ArrayList<TextProblem>()
      for (job in deferred) {
        val problems = job.await()
        coroutineToIndicator {
          for (problem in problems) {
            if (processProblem(problem, filtered)) {
              consumer(problem)
            }
          }
        }
      }
    }
  }

  private fun processProblem(problem: TextProblem, filtered: MutableList<TextProblem>): Boolean {
    require(problem.text == text)

    if (isSuppressed(problem) ||
        hasIgnoredCategory(problem) ||
        isIgnoredByStrategies(problem) ||
        ProblemFilter.allIgnoringFilters(problem).findAny().isPresent) {
      return false
    }

    if (filtered.none { it.highlightRanges.any { r1 -> problem.highlightRanges.any { r2 -> r1.intersects(r2) } } }) {
      filtered.add(problem)
      return true
    }
    return false
  }

  fun toProblemDescriptors(problem: TextProblem, isOnTheFly: Boolean): List<ProblemDescriptor> {
    val parent = text.commonParent
    val tooltip = problem.tooltipTemplate
    val description = problem.getDescriptionTemplate(isOnTheFly)
    return fileHighlightRanges(problem).map { range ->
      val descriptor = GrazieProblemDescriptor(parent, description, range.shiftLeft(parent.startOffset), isOnTheFly, tooltip)
      if (isOnTheFly) {
        descriptor.quickFixes = toFixes(problem, descriptor)
      }
      descriptor
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
    rangeInElement, true, onTheFly
  ) {
    var quickFixes: Array<LocalQuickFix> = LocalQuickFix.EMPTY_ARRAY

    override fun getFixes(): Array<LocalQuickFix> = quickFixes

    override fun getTooltipTemplate(): String {
      return tooltip
    }
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
    val rule = problem.rule
    result.add(object : GrazieAddExceptionQuickFix(suppressionPattern, underline) {
      override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
        GrazieFUSCounter.quickFixInvoked(rule, project, "add.exception")
        super.applyFix(project, psiFile, editor)
      }
    })
    result.add(GrazieRuleSettingsAction(problem.rule.presentableName, problem.rule))
    return result.toTypedArray()
  }

  private fun fileHighlightRanges(problem: TextProblem): List<TextRange> {
    return problem.highlightRanges.asSequence()
      .map { text.textRangeToFile(it) }
      .flatMap { range -> text.intersection(range) }
      .filterNot { it.isEmpty }
      .toList()
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
}
