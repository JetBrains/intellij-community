// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextProblem
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import kotlin.math.min

internal object GrazieReplaceTypoQuickFix {
  private class ReplaceTypoTitleAction(@IntentionFamilyName family: String, @IntentionName title: String) : ChoiceTitleIntentionAction(family, title),
    HighPriorityAction

  private class ChangeToVariantAction(
    private val rule: Rule,
    override val index: Int,
    @IntentionFamilyName private val family: String,
    @NlsSafe private val suggestion: String,
    private val replacement: String,
    private val underlineRanges: List<SmartPsiFileRange>,
    private val replacementRange: SmartPsiFileRange,
  )
    : ChoiceVariantIntentionAction(), HighPriorityAction {
    override fun getName(): String = suggestion.takeIf { it.isNotEmpty() } ?: msg("grazie.grammar.quickfix.remove.typo.tooltip")

    override fun getTooltipText(): String = if (suggestion.isNotEmpty()) {
      msg("grazie.grammar.quickfix.replace.typo.tooltip", suggestion)
    } else {
      msg("grazie.grammar.quickfix.remove.typo.tooltip")
    }

    override fun getFamilyName(): String = family

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean = replacementRange.range != null

    override fun getFileModifierForPreview(target: PsiFile): FileModifier = this

    override fun applyFix(project: Project, file: PsiFile, editor: Editor?) {
      GrazieFUSCounter.quickFixInvoked(rule, project, "accept.suggestion")

      val replacementRange = this.replacementRange.range ?: return
      val document = file.viewProvider.document ?: return

      underlineRanges.forEach { underline ->
        underline.range?.let { UpdateHighlightersUtil.removeHighlightersWithExactRange(document, project, it) }
      }

      document.replaceString(replacementRange.startOffset, replacementRange.endOffset, replacement)
    }

    override fun startInWriteAction(): Boolean = true
  }

  fun getReplacementFixes(problem: TextProblem, underlineRanges: List<SmartPsiFileRange>, file: PsiFile): List<LocalQuickFix> {
    val replacementRange = problem.replacementRange
    val replacedText = replacementRange.subSequence(problem.text)
    val spm = SmartPointerManager.getInstance(file.project)
    val familyName = GrazieBundle.message("grazie.grammar.quickfix.replace.typo.text", problem.shortMessage)
    val result = arrayListOf<LocalQuickFix>(ReplaceTypoTitleAction(familyName, problem.shortMessage))
    problem.corrections.forEachIndexed { index, suggestion ->
      val commonPrefix = StringUtil.commonPrefixLength(suggestion, replacedText)
      val commonSuffix =
        min(StringUtil.commonSuffixLength(suggestion, replacedText), min(suggestion.length, replacementRange.length) - commonPrefix)
      val localRange = TextRange(replacementRange.startOffset + commonPrefix, replacementRange.endOffset - commonSuffix)
      val replacement = suggestion.substring(commonPrefix, suggestion.length - commonSuffix)
      result.add(ChangeToVariantAction(
        problem.rule, index, familyName, suggestion, replacement, underlineRanges,
        spm.createSmartPsiFileRangePointer(file, problem.text.textRangeToFile(localRange))))
    }
    return result
  }
}
