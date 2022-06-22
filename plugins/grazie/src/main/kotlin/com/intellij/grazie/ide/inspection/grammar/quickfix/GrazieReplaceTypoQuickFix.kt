// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.CustomizableIntentionAction.RangeToHighlight
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.min

object GrazieReplaceTypoQuickFix {
  private class ReplaceTypoTitleAction(@IntentionFamilyName family: String, @IntentionName title: String) : ChoiceTitleIntentionAction(family, title),
    HighPriorityAction {
    override fun compareTo(other: IntentionAction): Int {
      if (other is GrazieCustomFixWrapper) return -1
      return super.compareTo(other)
    }
  }

  private class ChangeToVariantAction(
    private val rule: Rule,
    override val index: Int,
    @IntentionFamilyName private val family: String,
    @NlsSafe private val suggestion: String,
    private val replacements: List<Pair<SmartPsiFileRange, String>>,
    private val underlineRanges: List<SmartPsiFileRange>,
    private val toHighlight: List<SmartPsiFileRange>,
  )
    : ChoiceVariantIntentionAction(), HighPriorityAction {
    override fun getName(): String {
      if (suggestion.isEmpty()) return msg("grazie.grammar.quickfix.remove.typo.tooltip")
      if (suggestion[0].isWhitespace() || suggestion.last().isWhitespace()) return "'$suggestion'"
      return suggestion
    }

    override fun getTooltipText(): String = if (suggestion.isNotEmpty()) {
      msg("grazie.grammar.quickfix.replace.typo.tooltip", suggestion)
    } else {
      msg("grazie.grammar.quickfix.remove.typo.tooltip")
    }

    override fun getFamilyName(): String = family

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean = replacements.all { it.first.range != null }

    override fun getFileModifierForPreview(target: PsiFile): FileModifier = this

    override fun applyFix(project: Project, file: PsiFile, editor: Editor?) {
      GrazieFUSCounter.quickFixInvoked(rule, project, "accept.suggestion")

      val document = file.viewProvider.document ?: return

      underlineRanges.forEach { underline ->
        underline.range?.let { UpdateHighlightersUtil.removeHighlightersWithExactRange(document, project, it) }
      }

      applyReplacements(document, replacements)
    }

    override fun startInWriteAction(): Boolean = true

    override fun compareTo(other: IntentionAction): Int {
      if (other is GrazieCustomFixWrapper) return -1
      return super.compareTo(other)
    }

    override fun getRangesToHighlight(editor: Editor, _file: PsiFile): List<RangeToHighlight> {
      return toHighlight.mapNotNull {
        val range = it.range ?: return@mapNotNull null
        val file = it.containingFile ?: return@mapNotNull null
        RangeToHighlight(file, TextRange.create(range), EditorColors.SEARCH_RESULT_ATTRIBUTES)
      }
    }
  }

  @Deprecated(message = "use getReplacementFixes(problem, underlineRanges)")
  @Suppress("UNUSED_PARAMETER", "DeprecatedCallableAddReplaceWith")
  fun getReplacementFixes(problem: TextProblem, underlineRanges: List<SmartPsiFileRange>, file: PsiFile): List<LocalQuickFix> {
    return getReplacementFixes(problem, underlineRanges)
  }

  @JvmStatic
  fun getReplacementFixes(problem: TextProblem, underlineRanges: List<SmartPsiFileRange>): List<LocalQuickFix> {
    val file = problem.text.containingFile
    val spm = SmartPointerManager.getInstance(file.project)
    @Suppress("HardCodedStringLiteral") val familyName: @IntentionFamilyName String = familyName(problem)
    val result = arrayListOf<LocalQuickFix>(ReplaceTypoTitleAction(familyName, problem.shortMessage))
    problem.suggestions.forEachIndexed { index, suggestion ->
      val changes = suggestion.changes
      val replacements = changes.flatMap { toFileReplacements(it.range, it.replacement, problem.text) }
      val presentable = suggestion.presentableText
      val toHighlight = changes.map { spm.createSmartPsiFileRangePointer(file, makeNonEmpty(problem.text.textRangeToFile(it.range), file)) }
      result.add(ChangeToVariantAction(problem.rule, index, familyName, presentable, replacements, underlineRanges, toHighlight))
    }
    return result
  }

  @VisibleForTesting
  @JvmStatic
  fun toFileReplacements(replacementRange: TextRange, suggestion: CharSequence, text: TextContent): List<Pair<SmartPsiFileRange, String>> {
    val replacedText = replacementRange.subSequence(text)
    val commonPrefix = commonPrefixLength(suggestion, replacedText)
    val commonSuffix =
      min(commonSuffixLength(suggestion, replacedText), min(suggestion.length, replacementRange.length) - commonPrefix)
    val localRange = TextRange(replacementRange.startOffset + commonPrefix, replacementRange.endOffset - commonSuffix)
    val replacement = suggestion.substring(commonPrefix, suggestion.length - commonSuffix)

    val file = text.containingFile
    val spm = SmartPointerManager.getInstance(file.project)
    val shreds = text.intersection(text.textRangeToFile(localRange))
    if (shreds.isEmpty()) return emptyList()

    val best = if (isWordMiddle(text, localRange.endOffset)) shreds.last() else shreds.first()
    return shreds.map { spm.createSmartPsiFileRangePointer(file, it) to (if (it === best) replacement else "") }
  }

  private fun isWordMiddle(text: CharSequence, index: Int) =
    index > 0 && index < text.length && Character.isLetter(text[index]) && Character.isLetter(text[index - 1])

  @VisibleForTesting
  @JvmStatic
  fun applyReplacements(document: Document, replacements: List<Pair<SmartPsiFileRange, String>>) {
    replacements.forEach {
      document.replaceString(it.first.range!!.startOffset, it.first.range!!.endOffset, it.second)
    }
  }

  private fun makeNonEmpty(range: TextRange, file: PsiFile): TextRange {
    var start = range.startOffset
    var end = range.endOffset
    if (start == end) {
      if (end < file.textLength) end++
      else if (start > 0) start--
    }
    return TextRange(start, end)
  }

  fun familyName(problem: TextProblem): @IntentionFamilyName String =
    GrazieBundle.message("grazie.grammar.quickfix.replace.typo.text", problem.shortMessage)

  // custom common prefix/suffix calculation to honor cases when the text is separated by a synthetic \n,
  // but LT suggests a space instead (https://github.com/languagetool-org/languagetool/issues/5297)

  private fun commonPrefixLength(s1: CharSequence, s2: CharSequence): Int {
    val minLength = min(s1.length, s2.length)
    var i = 0
    while (i < minLength && charsMatch(s1[i], s2[i])) i++
    return i
  }

  private fun commonSuffixLength(s1: CharSequence, s2: CharSequence): Int {
    val minLength = min(s1.length, s2.length)
    var i = 0
    while (i < minLength && charsMatch(s1[s1.length - i - 1], s2[s2.length - i - 1])) i++
    return i
  }

  private fun charsMatch(c1: Char, c2: Char) = c1 == c2 || c1 == ' ' && c2 == '\n'
}
