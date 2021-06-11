// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInsight.intention.choice.DefaultIntentionActionWithChoice
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiFileRange

internal class GrazieReplaceTypoQuickFix(
  @NlsSafe private val message: String, private val fixes: List<String>,
  private val underlineRange: SmartPsiFileRange, private val replacementRange: SmartPsiFileRange
  )
  : DefaultIntentionActionWithChoice {
  private class ReplaceTypoTitleAction(@IntentionFamilyName family: String, @IntentionName title: String) : ChoiceTitleIntentionAction(family, title),
    HighPriorityAction

  override fun getTitle(): ChoiceTitleIntentionAction {
    return ReplaceTypoTitleAction(GrazieBundle.message("grazie.grammar.quickfix.replace.typo.text", message), message)
  }

  private inner class ChangeToVariantAction(
    override val index: Int,
    @IntentionFamilyName private val family: String,
    @NlsSafe private val suggestion: String
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
      val replacementRange = this@GrazieReplaceTypoQuickFix.replacementRange.range ?: return
      val document = file.viewProvider.document ?: return

      underlineRange.range?.let { UpdateHighlightersUtil.removeHighlightersWithExactRange(document, project, it) }

      document.replaceString(replacementRange.startOffset, replacementRange.endOffset, suggestion)
    }

    override fun startInWriteAction(): Boolean = true
  }

  override fun getVariants(): List<ChoiceVariantIntentionAction> {
    return fixes.withIndex().map { (index, suggestion) ->
      ChangeToVariantAction(index, msg("grazie.grammar.quickfix.replace.typo.text", message), suggestion)
    }
  }
}
