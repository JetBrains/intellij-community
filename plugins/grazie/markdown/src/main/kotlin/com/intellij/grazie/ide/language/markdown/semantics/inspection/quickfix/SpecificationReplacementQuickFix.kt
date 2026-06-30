package com.intellij.grazie.ide.language.markdown.semantics.inspection.quickfix

import com.intellij.codeInsight.intention.EventTrackingIntentionAction
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInsight.intention.choice.DefaultIntentionActionWithChoice
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix.removeHighlightersWithExactRange
import com.intellij.grazie.ide.language.markdown.semantics.fus.SpecificationFUSCollector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiFileRange
import org.jetbrains.annotations.Unmodifiable
import java.util.UUID

internal class SpecificationReplacementQuickFix(
  private val id: UUID,
  private val underline: SmartPsiFileRange,
  private val replacements: List<String>,
) : DefaultIntentionActionWithChoice {

  companion object {
    val familyName by lazy {
      GrazieBundle.message("specification.quick.fix.title.name")
    }
  }

  override fun getTitle(): ChoiceTitleIntentionAction = ReplacementTitle

  override fun getVariants(): @Unmodifiable List<ChoiceVariantIntentionAction> =
    replacements.mapIndexed { index, replacement -> ReplacementVariant(id, index, replacements.size, underline, replacement) }

  open class ReplacementVariant(
    private val id: UUID, override val index: Int, private val total: Int,
    private val underline: SmartPsiFileRange, @NlsSafe private val replacement: String,
  ) : ChoiceVariantIntentionAction(), HighPriorityAction, DumbAware, EventTrackingIntentionAction {

    override fun getName(): @IntentionName String = replacement
    override fun getFamilyName(): @IntentionFamilyName String = Companion.familyName
    override fun getFileModifierForPreview(target: PsiFile): FileModifier = ReplacementPreview(id, index, total, underline, replacement)
    override fun isShowSubmenu(): Boolean = true

    override fun applyFix(project: Project, file: PsiFile, editor: Editor?) {
      SpecificationFUSCollector.suggestionAccepted(id, index, total)
      performFix(project, file, replacement)
    }

    override fun suggestionShown(project: Project, editor: Editor, psiFile: PsiFile) =
      SpecificationFUSCollector.suggestionShown(id, index, total)

    internal fun performFix(project: Project, file: PsiFile, replacement: String) {
      val document = file.viewProvider.document ?: return
      val range = underline.range ?: return
      removeHighlightersWithExactRange(document, project, range)
      document.replaceString(range.startOffset, range.endOffset, replacement)
    }
  }

  private object ReplacementTitle : ChoiceTitleIntentionAction(familyName, familyName), HighPriorityAction, DumbAware

  private class ReplacementPreview(
    id: UUID, index: Int, total: Int, underline: SmartPsiFileRange,
    private val replacement: String,
  ) : ReplacementVariant(id, index, total, underline, replacement), IntentionPreviewInfo {
    override fun applyFix(project: Project, file: PsiFile, editor: Editor?) = performFix(project, file, replacement)
  }
}
