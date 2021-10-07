@file:Suppress("DEPRECATION")

package com.intellij.grazie.text

import ai.grazie.nlp.tokenizer.sentence.SRXSentenceTokenizer
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieAddExceptionQuickFix
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieRuleSettingsAction
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
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
    return problems.flatMap { problem ->
      fileHighlightRanges(problem).map { range ->
        ProblemDescriptorBase(
          parent, parent, problem.getDescriptionTemplate(isOnTheFly),
          if (isOnTheFly) toFixes(problem) else LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false,
          range.shiftLeft(parent.startOffset),
          true, isOnTheFly)
      }
    }
  }

  private fun isIgnoredByFilters(problem: TextProblem) =
    filterEp.allForLanguageOrAny(problem.text.commonParent.language).any { it.shouldIgnore(problem) }

  private fun isIgnoredByStrategies(descriptor: TextProblem): Boolean {
    for (root in text.findPsiElementAt(0).parents(withSelf = true)) {
      for (strategy in LanguageGrammarChecking.allForLanguage(root.language)) {
        if (strategy.isMyContextRoot(root)) {
          val errorRange = text.textRangeToFile(descriptor.highlightRange).shiftLeft(root.startOffset)
          val patternRange = text.textRangeToFile(descriptor.patternRange ?: descriptor.highlightRange).shiftLeft(root.startOffset)
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
    val psiRange = text.textRangeToFile(descriptor.highlightRange)
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
    if (defaultSuppressionPattern(problem, sentence).isSuppressed()) {
      return true
    }

    val patternRange = problem.patternRange
    val errorText = problem.highlightRange.subSequence(text)
    return patternRange != null && sentence != null && SuppressionPattern(errorText, sentence).isSuppressed()
  }

  private fun findSentence(problem: TextProblem) =
    sentences.find { problem.highlightRange.intersects(it.range.first, it.range.last + 1) }?.token

  fun toFixes(problem: TextProblem): Array<LocalQuickFix> {
    val file = text.commonParent.containingFile
    val result = arrayListOf<LocalQuickFix>()
    val spm = SmartPointerManager.getInstance(file.project)
    val underline = fileHighlightRanges(problem).map { spm.createSmartPsiFileRangePointer(file, it) }

    val fixes = problem.corrections
    if (fixes.isNotEmpty()) {
      GrazieFUSCounter.typoFound(problem)
      result.addAll(GrazieReplaceTypoQuickFix.getReplacementFixes(problem, underline, file))
    }

    result.add(object : GrazieAddExceptionQuickFix(defaultSuppressionPattern(problem, findSentence(problem)), underline) {
      override fun applyFix(project: Project, file: PsiFile, editor: Editor?) {
        GrazieFUSCounter.quickFixInvoked(problem.rule, project, "add.exception")
        super.applyFix(project, file, editor)
      }
    })
    result.add(GrazieRuleSettingsAction(problem.rule.presentableName, problem.rule))
    return result.toTypedArray()
  }

  private fun fileHighlightRanges(problem: TextProblem): List<TextRange> {
    val range = text.textRangeToFile(problem.highlightRange)
    return text.rangesInFile.asSequence().mapNotNull { it.intersection(range) }.filterNot { it.isEmpty }.toList()
  }

  private fun defaultSuppressionPattern(problem: TextProblem, sentenceText: String?): SuppressionPattern {
    val text = problem.text
    val patternRange = problem.patternRange
    if (patternRange != null) {
      return SuppressionPattern(patternRange.subSequence(text), null)
    }
    return SuppressionPattern(problem.highlightRange.subSequence(text), sentenceText)
  }
}

private val filterEp = LanguageExtension<ProblemFilter>("com.intellij.grazie.problemFilter")