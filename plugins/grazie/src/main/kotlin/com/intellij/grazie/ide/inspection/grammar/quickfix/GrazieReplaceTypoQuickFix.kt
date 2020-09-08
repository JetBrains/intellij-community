// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInsight.intention.choice.DefaultIntentionActionWithChoice
import com.intellij.codeInsight.intention.impl.config.LazyEditor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset

class GrazieReplaceTypoQuickFix(private val typo: Typo) : DefaultIntentionActionWithChoice {

  private class ReplaceTypoTitleAction(@IntentionFamilyName family: String, @IntentionName title: String) : ChoiceTitleIntentionAction(family, title), HighPriorityAction

  override fun getTitle(): ChoiceTitleIntentionAction {
    return ReplaceTypoTitleAction(msg("grazie.grammar.quickfix.replace.typo.text", typo.info.shortMessage), typo.info.shortMessage)
  }

  private inner class ChangeToVariantAction(
    override val index: Int,
    @IntentionFamilyName private val family: String,
    @NlsSafe private val suggestion: String
  )
    : ChoiceVariantIntentionAction(), HighPriorityAction {
    override fun getName(): String = suggestion

    override fun getTooltipText(): String = GrazieBundle.message("grazie.grammar.quickfix.replace.typo.tooltip", suggestion)

    override fun getFamilyName(): String = family

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean = typo.location.element != null

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
      return this
    }

    override fun applyFix(project: Project, file: PsiFile, editor: Editor?) {
      val myEditor = editor ?: LazyEditor(file)

      val element = typo.location.element ?: return
      val range = TextRange.create(typo.location.errorRange.start, typo.location.errorRange.endInclusive + 1).shiftRight(element.startOffset)


      val text = myEditor.document.getText(range)
      if (text != typo.location.errorText) return

      myEditor.document.replaceString(range.startOffset, range.endOffset, suggestion)
    }

    override fun startInWriteAction(): Boolean = true
  }

  override fun getVariants(): List<ChoiceVariantIntentionAction> {
    return typo.fixes.withIndex().map { (index, suggestion) ->
      ChangeToVariantAction(index, msg("grazie.grammar.quickfix.replace.typo.text", typo.info.shortMessage), suggestion)
    }
  }
}
