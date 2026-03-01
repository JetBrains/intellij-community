// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PriorityAction.Priority
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle

private val EP_NAME = ExtensionPointName.create<UseHtmlChunkToolTipFixProvider>("DevKit.lang.useHtmlChunkToolTipFixProvider")

internal object UseHtmlChunkToolTipFixProviders : LanguageExtension<UseHtmlChunkToolTipFixProvider>(EP_NAME.name)

@IntellijInternalApi
@ApiStatus.Internal
interface UseHtmlChunkToolTipFixProvider {
  fun createFixes(element: PsiElement): Array<LocalQuickFix>
}

private const val SET_TOOLTIP_TEXT_METHOD_FQN = "com.intellij.ide.HelpTooltipKt.setToolTipText"
private const val HTML_CHUNK_FQN = "com.intellij.openapi.util.text.HtmlChunk"

internal class JavaUseHtmlChunkToolTipFixProvider : UseHtmlChunkToolTipFixProvider {
  override fun createFixes(element: PsiElement): Array<LocalQuickFix> {
    return arrayOf(
      JavaWrapWithHtmlChunkFix("text", "inspections.use.html.chunk.tooltip.fix.wrap.text.family.name", Priority.TOP),
      JavaWrapWithHtmlChunkFix("raw", "inspections.use.html.chunk.tooltip.fix.wrap.raw.family.name", Priority.HIGH),
    )
  }
}

/**
 * Replaces `comp.setToolTipText(arg)` with `HelpTooltipKt.setToolTipText(comp, HtmlChunk.text/raw(arg))`.
 */
private class JavaWrapWithHtmlChunkFix(
  private val wrapMethodName: String,
  private val familyNameKey: String,
  private val fixPriority: Priority,
) : LocalQuickFix, PriorityAction {
  override fun getPriority(): Priority = fixPriority

  @IntentionFamilyName
  override fun getFamilyName(): String = DevKitBundle.message(familyNameKey)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val callExpr = descriptor.psiElement as? PsiMethodCallExpression ?: return
    val qualifierExpr = callExpr.methodExpression.qualifierExpression ?: return
    val arg = callExpr.argumentList.expressions.firstOrNull() ?: return

    val newText = "$SET_TOOLTIP_TEXT_METHOD_FQN(${qualifierExpr.text}, $HTML_CHUNK_FQN.$wrapMethodName(${arg.text}))"
    val newExpr = PsiElementFactory.getInstance(project).createExpressionFromText(newText, callExpr)
    val replaced = callExpr.replace(newExpr)
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced)
  }
}
