// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.CancellationCheckInLoopsFixProvider
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.AddBracesIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSemicolon
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil.findChildByType


internal class KtCancellationCheckInLoopsFixProvider : CancellationCheckInLoopsFixProvider {

  override fun getFixes(loopKeyword: PsiElement, cancellationCheckFqn: String): List<LocalQuickFix> {
    return listOf(KtInsertCancellationCheckFix(cancellationCheckFqn, loopKeyword))
  }

}

internal class KtInsertCancellationCheckFix(
  private val cancellationCheckCallFqn: String,
  loopKeyword: PsiElement,
) : LocalQuickFixOnPsiElement(loopKeyword) {

  override fun getFamilyName(): String = DevKitBundle.message("inspection.insert.cancellation.check.fix.message")

  override fun getText(): String = familyName

  override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
    return startElement.parentOfType<KtLoopExpression>() != null
  }

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val loopStatement = startElement.parentOfType<KtLoopExpression>() ?: return

    val factory = KtPsiFactory(project)
    val cancellationCheckExpression = factory.createExpression("${cancellationCheckCallFqn}()")

    val bodyBlock = loopStatement.getOrCreateBodyBlock(project)

    bodyBlock?.addExpressionToFirstLine(cancellationCheckExpression)?.let {
      ShortenReferencesFacility.getInstance().shorten(it)
    }
  }

  private fun KtLoopExpression.getOrCreateBodyBlock(project: Project): KtBlockExpression? {
    return when (val loopBody = body) {
      // loops with body blocks
      is KtBlockExpression -> loopBody
      // single-line loops
      is KtExpression -> {
        AddBracesIntention.Util.addBraces(this, loopBody)
        body as KtBlockExpression
      }
      // no-body loops like `for (i in 1..10);`
      else -> {
        val containerNode = findChildByType(this, KtNodeTypes.BODY) ?: return null
        containerNode.add(KtPsiFactory(project).createEmptyBody())
        deleteRedundantSemicolon(this)
        body as KtBlockExpression
      }
    }
  }

  private fun deleteRedundantSemicolon(loop: KtLoopExpression) {
    val nextSibling = loop.nextSibling
    if (nextSibling.node.elementType == KtTokens.SEMICOLON && isRedundantSemicolon(nextSibling)) {
      nextSibling.delete()
    }
  }

  private fun KtBlockExpression.addExpressionToFirstLine(expression: KtExpression): KtExpression? {
    // otherwise the code might become incorrect in case of poor formatting before inserting an expression (e.g. missing new lines)
    CodeStyleManager.getInstance(project).reformat(this)
    return addAfter(expression, lBrace) as? KtExpression
  }

}
