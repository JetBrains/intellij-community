// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.kotlin.idea.codeinsight.utils.AddBracesUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSemicolon
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil.findChildByType

internal class KtCancellationCheckInLoopsFixProvider : CancellationCheckInLoopsFixProvider {

  override fun getFixes(anchor: PsiElement, cancellationCheckFqn: String): List<LocalQuickFix> {
    return when {
      anchor.node.elementType == KtTokens.IDENTIFIER -> listOf(KtInsertCancellationCheckInLoopMethodFix(cancellationCheckFqn, anchor))
      else -> listOf(KtInsertCancellationCheckInLoopFix(cancellationCheckFqn, anchor))
    }
  }
}

internal class KtInsertCancellationCheckInLoopFix(cancellationCheckCallFqn: String, loopKeyword: PsiElement)
  : AbstractKtInsertCancellationCheckFix(cancellationCheckCallFqn, loopKeyword) {
  override fun getBlockExpression(startElement: PsiElement): KtBlockExpression? {
    val loopStatement = startElement.parentOfType<KtLoopExpression>() ?: return null
    return loopStatement.getOrCreateBodyBlock(startElement.project)
  }

  override fun addExpression(bodyBlock: KtBlockExpression, cancellationCheckExpression: KtExpression): KtExpression? {
    val project = bodyBlock.project
    CodeStyleManager.getInstance(project).reformat(bodyBlock)
    return bodyBlock.addAfter(cancellationCheckExpression, bodyBlock.lBrace) as? KtExpression
  }

  private fun KtLoopExpression.getOrCreateBodyBlock(project: Project): KtBlockExpression? {
    return when (val loopBody = body) {
      // loops with body blocks
      is KtBlockExpression -> loopBody
      // single-line loops
      is KtExpression -> {
        AddBracesUtils.addBraces(this, loopBody)
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

  override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
    return startElement.parentOfType<KtLoopExpression>() != null
  }
}

internal class KtInsertCancellationCheckInLoopMethodFix(cancellationCheckCallFqn: String, loopKeyword: PsiElement)
  : AbstractKtInsertCancellationCheckFix(cancellationCheckCallFqn, loopKeyword) {

  override fun getBlockExpression(startElement: PsiElement): KtBlockExpression? {
    return startElement.parentOfType<KtCallExpression>()?.lambdaArguments?.firstOrNull()?.getLambdaExpression()?.bodyExpression
  }

  override fun addExpression(bodyBlock: KtBlockExpression, cancellationCheckExpression: KtExpression): KtExpression? {
    val project = bodyBlock.project
    val insertedExpression = bodyBlock.addAfter(cancellationCheckExpression, bodyBlock.lBrace) as? KtExpression
    insertedExpression?.parent?.addAfter(KtPsiFactory(project).createNewLine(), insertedExpression)
    bodyBlock.parentOfType<KtLambdaExpression>()?.let {
      CodeStyleManager.getInstance(project).reformat(it)
    }
    return insertedExpression
  }
}

internal abstract class AbstractKtInsertCancellationCheckFix(
  private val cancellationCheckCallFqn: String,
  loopKeyword: PsiElement,
) : LocalQuickFixOnPsiElement(loopKeyword) {

  override fun getFamilyName(): String = DevKitBundle.message("inspection.insert.cancellation.check.fix.message")

  override fun getText(): String = familyName

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val bodyBlock = getBlockExpression(startElement) ?: return
    val cancellationCheckExpression = KtPsiFactory(project).createExpression("${cancellationCheckCallFqn}()")
    addExpression(bodyBlock, cancellationCheckExpression)?.let {
      ShortenReferencesFacility.getInstance().shorten(it)
    }
  }

  abstract fun getBlockExpression(startElement: PsiElement): KtBlockExpression?
  abstract fun addExpression(bodyBlock: KtBlockExpression, cancellationCheckExpression: KtExpression): KtExpression?

}
