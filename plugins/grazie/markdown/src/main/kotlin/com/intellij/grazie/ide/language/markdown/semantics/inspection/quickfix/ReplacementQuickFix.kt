package com.intellij.grazie.ide.language.markdown.semantics.inspection.quickfix

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInsight.intention.choice.DefaultIntentionActionWithChoice
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix.removeHighlightersWithExactRange
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiFileRange
import org.intellij.plugins.markdown.MarkdownBundle
import org.jetbrains.annotations.Unmodifiable

internal class ReplacementQuickFix(private val underline: SmartPsiFileRange, private val replacements: List<String>): DefaultIntentionActionWithChoice {

  companion object {
    val familyName by lazy {
      MarkdownBundle.message("markdown.specification.quick.fix.title.name")
    }
  }

  override fun getTitle(): ChoiceTitleIntentionAction = ReplacementTitle

  override fun getVariants(): @Unmodifiable List<ChoiceVariantIntentionAction> =
    replacements.mapIndexed { index, replacement -> ReplacementVariant(index, underline, replacement) }

  open class ReplacementVariant(override val index: Int, private val underline: SmartPsiFileRange, @NlsSafe private val replacement: String) :
    ChoiceVariantIntentionAction(), HighPriorityAction, DumbAware {

    override fun getName(): @IntentionName String = replacement
    override fun getFamilyName(): @IntentionFamilyName String = Companion.familyName
    override fun getFileModifierForPreview(target: PsiFile): FileModifier = ReplacementPreview(index, underline, replacement)

    override fun applyFix(project: Project, file: PsiFile, editor: Editor?) {
      val document = file.viewProvider.document ?: return
      val range = underline.range ?: return
      removeHighlightersWithExactRange(document, project, range)
      document.replaceString(range.startOffset, range.endOffset, replacement)
    }
  }

  private object ReplacementTitle: ChoiceTitleIntentionAction(familyName, familyName), HighPriorityAction, DumbAware

  private class ReplacementPreview(index: Int, underline: SmartPsiFileRange, replacement: String):
    ReplacementVariant(index, underline, replacement), IntentionPreviewInfo
}
