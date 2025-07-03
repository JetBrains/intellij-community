// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption
import com.intellij.codeInsight.intention.*
import com.intellij.codeInsight.intention.CustomizableIntentionAction.RangeToHighlight
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInsight.intention.EventTrackingIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.ide.fus.AcceptanceRateTracker
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.min

object GrazieReplaceTypoQuickFix {
  private class ReplaceTypoTitleAction(@IntentionFamilyName family: String, @IntentionName title: String) :
    ChoiceTitleIntentionAction(family, title), HighPriorityAction, DumbAware {
    override fun compareTo(other: IntentionAction): Int {
      if (other is GrazieCustomFixWrapper) return -1
      return super.compareTo(other)
    }
  }

  private open class ChangeToVariantAction(
    override val index: Int,
    private val total: Int,
    @IntentionFamilyName private val family: String,
    @NlsSafe private val suggestion: String,
    private val replacements: List<Pair<SmartPsiFileRange, String>>,
    private val underlineRanges: List<SmartPsiFileRange>,
    private val toHighlight: List<SmartPsiFileRange>,
    private val batchId: String?,
    private val tracker: AcceptanceRateTracker,
  ) : ChoiceVariantIntentionAction(), HighPriorityAction, IntentionActionWithFixAllOption, DumbAware, EventTrackingIntentionAction {
    override fun getName(): String {
      if (suggestion.isEmpty()) return msg("grazie.grammar.quickfix.remove.typo.tooltip")
      if (suggestion[0].isWhitespace() || suggestion.last().isWhitespace()) return "'$suggestion'"
      return suggestion
    }

    override fun isShowSubmenu(): Boolean {
      return batchId != null
    }

    override fun getOptions(): List<IntentionAction> {
      return if (batchId == null) listOf() else super.getOptions()
    }

    override fun belongsToMyFamily(action: IntentionActionWithFixAllOption): Boolean {
      return action is ChangeToVariantAction && action.batchId == this.batchId
    }

    override fun getFixAllText(): String {
      return GrazieBundle.message("grazie.grammar.quickfix.apply.batch.text")
    }

    override fun getCombiningPolicy(): IntentionActionWithOptions.CombiningPolicy {
      return IntentionActionWithOptions.CombiningPolicy.IntentionOptionsOnly
    }

    override fun getTooltipText(): String = if (suggestion.isNotEmpty()) {
      msg("grazie.grammar.quickfix.replace.typo.tooltip", suggestion)
    } else {
      msg("grazie.grammar.quickfix.remove.typo.tooltip")
    }

    override fun getFamilyName(): String = family

    override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile): Boolean = replacements.all { it.first.range != null }

    override fun getFileModifierForPreview(target: PsiFile): FileModifier {
      return ForPreview(index, total, family, suggestion, replacements, underlineRanges, toHighlight, batchId, tracker)
    }

    override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
      GrazieFUSCounter.suggestionAccepted(project, tracker, index, total)
      performFix(project, psiFile)
    }

    protected fun performFix(project: Project, file: PsiFile) {
      val document = file.viewProvider.document ?: return
      underlineRanges.forEach { underline ->
        underline.range?.let { removeHighlightersWithExactRange(document, project, it) }
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

   override fun suggestionShown(project: Project, editor: Editor, psiFile: PsiFile) {
      if (tracker.markShown()) {
        GrazieFUSCounter.suggestionShown(project, tracker)
      }
    }

    private class ForPreview(
      index: Int,
      total: Int,
      @IntentionFamilyName family: String,
      @NlsSafe suggestion: String,
      replacements: List<Pair<SmartPsiFileRange, String>>,
      underlineRanges: List<SmartPsiFileRange>,
      toHighlight: List<SmartPsiFileRange>,
      batchId: String?,
      tracker: AcceptanceRateTracker,
    ) : ChangeToVariantAction(index, total, family, suggestion, replacements, underlineRanges, toHighlight, batchId, tracker), IntentionPreviewInfo {
      override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
        performFix(project, psiFile)
      }
    }
  }

  @JvmStatic
  fun getReplacementFixes(problem: TextProblem, underlineRanges: List<SmartPsiFileRange>): List<LocalQuickFix> {
    val file = problem.text.containingFile
    val spm = SmartPointerManager.getInstance(file.project)
    val familyName: @IntentionFamilyName String = familyName(problem)
    val result = arrayListOf<LocalQuickFix>(ReplaceTypoTitleAction(familyName, problem.shortMessage))
    val suggestions = problem.suggestions.asSequence().take(15)
    suggestions.forEachIndexed { index, suggestion ->
      val changes = suggestion.changes
      val replacements = changes.flatMap { toFileReplacements(it.range, it.replacement, problem.text) }
      val presentable = suggestion.presentableText
      val toHighlight = changes.map { spm.createSmartPsiFileRangePointer(file, makeNonEmpty(problem.text.textRangeToFile(it.range), file)) }
      val tracker = AcceptanceRateTracker(problem)
      result.add(
        ChangeToVariantAction(index, problem.suggestions.size,
                              familyName, presentable, replacements,
                              underlineRanges, toHighlight,
                              suggestion.batchId, tracker
        )
      )
    }
    return result
  }

  @VisibleForTesting
  @JvmStatic
  fun toFileReplacements(replacementRange: TextRange, suggestion: CharSequence, text: TextContent): List<Pair<SmartPsiFileRange, String>> {
    val replacedText = replacementRange.subSequence(text)
    val file = text.containingFile
    val spm = SmartPointerManager.getInstance(file.project)
    if (replacedText.contains(Regex("(- *\n)|(\n *-)"))) {
      val fileRange = text.textRangeToFile(replacementRange)
      return listOf(spm.createSmartPsiFileRangePointer(file, fileRange) to suggestion.toString())
    }
    val commonPrefix = commonPrefixLength(suggestion, replacedText)
    val commonSuffix =
      min(commonSuffixLength(suggestion, replacedText), min(suggestion.length, replacementRange.length) - commonPrefix)
    val localRange = TextRange(replacementRange.startOffset + commonPrefix, replacementRange.endOffset - commonSuffix)
    var replacement = suggestion.substring(commonPrefix, suggestion.length - commonSuffix)

    val shreds = text.intersection(text.textRangeToFile(localRange))
    if (shreds.isEmpty()) return emptyList()

    if (replacement.isEmpty() && removalWouldGlueUnrelatedTokens(localRange, text)) {
      replacement = " "
    }

    val best = if (isWordMiddle(text, localRange.endOffset)) shreds.last() else shreds.first()
    return shreds.map { spm.createSmartPsiFileRangePointer(file, it) to (if (it === best) replacement else "") }
  }

  private fun removalWouldGlueUnrelatedTokens(removedRange: TextRange, text: TextContent): Boolean {
    val prevFileIndex = text.textOffsetToFile(0) - 1
    return removedRange.endOffset < text.length && text[removedRange.endOffset].isLetterOrDigit() &&
           prevFileIndex > 0 && text.containingFile.viewProvider.contents[prevFileIndex].isLetterOrDigit()
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

  /**
   * Remove all highlighters with exactly the given range from [DocumentMarkupModel].
   * This might be useful in quick fixes and intention actions to provide immediate feedback.
   * Note that all highlighters at the given range are removed, not only the ones produced by your inspection,
   * but most likely that will look fine:
   * they'll be restored when the new highlighting pass is finished.
   * This method currently works in O(total highlighter count in file) time.
   */
  fun removeHighlightersWithExactRange(document: Document, project: Project, range: Segment) {
    if (IntentionPreviewUtils.isIntentionPreviewActive()) return
    ThreadingAssertions.assertEventDispatchThread()
    val model = DocumentMarkupModel.forDocument(document, project, false) ?: return

    for (highlighter in model.allHighlighters) {
      if (TextRange.areSegmentsEqual(range, highlighter)) {
        model.removeHighlighter(highlighter)
      }
    }
  }
}