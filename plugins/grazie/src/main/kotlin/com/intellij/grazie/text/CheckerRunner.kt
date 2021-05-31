@file:Suppress("DEPRECATION")

package com.intellij.grazie.text

import ai.grazie.nlp.tokenizer.sentence.SRXSentenceTokenizer
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieAddExceptionQuickFix
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieDisableRuleQuickFix
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.startOffset

internal class CheckerRunner(val text: TextContent) {
  private val sentences by lazy { SRXSentenceTokenizer.tokenize(text.toString()) }

  fun run(checkers: List<TextChecker>): List<TextProblem> {
    val filtered = ArrayList<TextProblem>()
    for (checker in checkers) {
      for (problem in checker.check(text)) {
        require(problem.text == text)

        if (isSuppressed(problem) ||
            hasIgnoredCategory(problem) ||
            isIgnoredByStrategies(problem) ||
            isIgnoredByFilters(problem)) {
          continue
        }

        if (filtered.none { it.highlightRange.intersects(problem.highlightRange) }) {
          filtered.add(problem)
        }
      }
    }

    return filtered
  }

  fun toProblemDescriptors(problems: List<TextProblem>, isOnTheFly: Boolean): List<ProblemDescriptor> {
    val parent = text.commonParent
    return problems.map { problem ->
      ProblemDescriptorBase(
        parent, parent, problem.getDescriptionTemplate(isOnTheFly),
        if (isOnTheFly) toFixes(problem) else LocalQuickFix.EMPTY_ARRAY,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false,
        toFileRange(problem.highlightRange).shiftLeft(parent.startOffset),
        true, isOnTheFly)
    }
  }

  private fun isIgnoredByFilters(problem: TextProblem) =
    filterEp.allForLanguageOrAny(problem.text.commonParent.language).any { it.shouldIgnore(problem) }

  private fun isIgnoredByStrategies(descriptor: TextProblem): Boolean {
    for (root in text.findPsiElementAt(0).parents(withSelf = true)) {
      for (strategy in LanguageGrammarChecking.allForLanguage(root.language)) {
        if (strategy.isMyContextRoot(root)) {
          val errorRange = toFileRange(descriptor.highlightRange).shiftLeft(root.startOffset)
          val patternRange = toFileRange(descriptor.patternRange ?: descriptor.highlightRange).shiftLeft(root.startOffset)
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
    val psiRange = toFileRange(descriptor.highlightRange)
    val textRange = text.fileRangeToText(psiRange) ?: return RuleGroup.EMPTY
    val leaves = (textRange.startOffset until textRange.endOffset).map { text.findPsiElementAt(it) }.toLinkedSet()
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
    val defaultPattern = defaultSuppressionPattern(problem, sentence)
    val suppressed = GrazieConfig.get().suppressingContext.suppressed
    if (defaultPattern.full in suppressed) {
      return true
    }

    val patternRange = problem.patternRange
    val errorText = problem.highlightRange.subSequence(text)
    return patternRange != null && sentence != null && SuppressionPattern(errorText, sentence).full in suppressed
  }

  private fun findSentence(problem: TextProblem) =
    sentences.find { problem.highlightRange.intersects(it.range.first, it.range.last + 1) }?.token

  fun toFixes(problem: TextProblem): Array<LocalQuickFix> {
    val file = text.commonParent.containingFile
    val result = arrayListOf<LocalQuickFix>()
    val spm = SmartPointerManager.getInstance(file.project)
    val underline = spm.createSmartPsiFileRangePointer(file, toFileRange(problem.highlightRange))

    val fixes = problem.corrections
    if (fixes.isNotEmpty()) {
      GrazieFUSCounter.typoFound(problem)
      val replace = spm.createSmartPsiFileRangePointer(file, toFileRange(problem.replacementRange))
      result.addAll(GrazieReplaceTypoQuickFix(problem.shortMessage, fixes, underline, replace).getAllAsFixes())
    }

    result.add(GrazieAddExceptionQuickFix(defaultSuppressionPattern(problem, findSentence(problem)), underline))
    result.add(GrazieDisableRuleQuickFix(problem.shortMessage, problem.rule))
    return result.toTypedArray()
  }

  private fun toFileRange(range: TextRange) =
    TextRange(text.textOffsetToFile(range.startOffset), text.textOffsetToFile(range.endOffset))

  private fun defaultSuppressionPattern(problem: TextProblem, sentenceText: String?): SuppressionPattern {
    val text = problem.text
    val patternRange = problem.patternRange
    if (patternRange != null) {
      return SuppressionPattern(patternRange.subSequence(text), null)
    }
    return SuppressionPattern(problem.highlightRange.subSequence(text), sentenceText)
  }
}

internal class SuppressionPattern(errorText: CharSequence, sentenceText: String?) {
  val errorText : String = normalize(errorText)
  val full : String = this.errorText + (if (sentenceText == null) "" else "|" + normalize(sentenceText))

  private fun normalize(text: CharSequence) = text.replace(Regex("\\s+"), " ").trim()
}

private val filterEp = LanguageExtension<ProblemFilter>("com.intellij.grazie.problemFilter")