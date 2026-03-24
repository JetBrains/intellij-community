// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PriorityAction.Priority
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.UseHtmlChunkToolTipFixProvider
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class KtUseHtmlChunkToolTipFixProvider : UseHtmlChunkToolTipFixProvider {
  override fun createFixes(element: PsiElement): Array<LocalQuickFix> {
    return arrayOf(
      KtWrapWithHtmlChunkFix("text", "inspections.use.html.chunk.tooltip.fix.wrap.text.family.name", Priority.TOP),
      KtWrapWithHtmlChunkFix("raw", "inspections.use.html.chunk.tooltip.fix.wrap.raw.family.name", Priority.HIGH),
    )
  }
}

private const val HTML_CHUNK_FQN = "com.intellij.openapi.util.text.HtmlChunk"
private val SET_TOOL_TIP_TEXT_EXTENSION_FQN = FqName("com.intellij.ide.setToolTipText")

private class KtWrapWithHtmlChunkFix(
  private val wrapMethodName: String,
  private val familyNameKey: String,
  private val fixPriority: Priority,
) : LocalQuickFix, PriorityAction {
  override fun getPriority(): Priority = fixPriority

  @IntentionFamilyName
  override fun getFamilyName(): String = DevKitBundle.message(familyNameKey)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement
    val ktFile = element.containingFile as? KtFile ?: return
    val psiFactory = KtPsiFactory(project)

    // Case 1: Method call — comp.setToolTipText(arg)
    val dotCall = element as? KtDotQualifiedExpression
                  ?: (element.parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression is KtCallExpression }
    if (dotCall != null) {
      val call = dotCall.selectorExpression as? KtCallExpression ?: return
      val receiverText = dotCall.receiverExpression.text
      val argText = call.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: return
      replaceAndShortenReferences(ktFile, psiFactory, dotCall, receiverText, argText)
      return
    }

    // Case 2: Property assignment — comp.toolTipText = arg
    val binaryExpr = element as? KtBinaryExpression ?: return
    val left = binaryExpr.left as? KtDotQualifiedExpression ?: return
    val receiverText = left.receiverExpression.text
    val argText = binaryExpr.right?.text ?: return
    replaceAndShortenReferences(ktFile, psiFactory, binaryExpr, receiverText, argText)
  }

  private fun replaceAndShortenReferences(
    ktFile: KtFile,
    psiFactory: KtPsiFactory,
    nodeToReplace: PsiElement,
    receiverText: String,
    argText: String,
  ) {
    val newExpr = psiFactory.createExpression("$receiverText.setToolTipText($HTML_CHUNK_FQN.$wrapMethodName($argText))")
    val replaced = nodeToReplace.replace(newExpr)
    ShortenReferencesFacility.getInstance().shorten(replaced as org.jetbrains.kotlin.psi.KtElement)
    ktFile.addImport(SET_TOOL_TIP_TEXT_EXTENSION_FQN)
  }
}
