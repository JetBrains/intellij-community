// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.CancellationExceptionHandlingFixProvider
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

/**
 * Kotlin call form of the `rethrowControlFlowException` top-level helper.
 */
private const val RETHROW_CONTROL_FLOW_EXCEPTION_KOTLIN_FQN: String = "com.intellij.diagnostic.rethrowControlFlowException"

internal class KtCancellationExceptionHandlingFixProvider : CancellationExceptionHandlingFixProvider {

  override fun getRemoveLoggingStatementFix(loggingCall: PsiElement): LocalQuickFix? {
    val statement = (loggingCall as? KtExpression)?.getQualifiedExpressionForSelectorOrThis() ?: return null
    if (statement.parent !is KtBlockExpression) return null
    return LocalQuickFix.from(KtRemoveLoggingStatementFix(statement))
  }

  override fun getInsertRethrowControlFlowExceptionFix(caughtExceptionParameter: PsiElement): LocalQuickFix? {
    val parameter = caughtExceptionParameter as? KtParameter ?: return null
    return LocalQuickFix.from(KtInsertRethrowHelperFix(parameter))
  }

  override fun getReplaceGuardWithRethrowHelperFix(guardStatement: PsiElement): LocalQuickFix? {
    val ifExpression = guardStatement as? KtIfExpression ?: return null
    return LocalQuickFix.from(KtReplaceGuardFix(ifExpression))
  }
}

private class KtRemoveLoggingStatementFix(statement: KtExpression) : PsiUpdateModCommandAction<KtExpression>(statement) {

  override fun getFamilyName(): String {
    return DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name")
  }

  override fun invoke(context: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
    element.delete()
  }
}

private class KtInsertRethrowHelperFix(parameter: KtParameter) : PsiUpdateModCommandAction<KtParameter>(parameter) {

  override fun getFamilyName(): String {
    return DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name")
  }

  override fun invoke(context: ActionContext, element: KtParameter, updater: ModPsiUpdater) {
    val block = element.parentOfType<KtCatchClause>()?.catchBody as? KtBlockExpression ?: return
    val factory = KtPsiFactory(context.project())
    val helperCall = factory.createExpression("$RETHROW_CONTROL_FLOW_EXCEPTION_KOTLIN_FQN(${element.name})")
    val inserted = block.addAfter(helperCall, block.lBrace) as KtElement
    block.addAfter(factory.createNewLine(), block.lBrace)
    ShortenReferencesFacility.getInstance().shorten(inserted)
    CodeStyleManager.getInstance(context.project()).reformat(block)
  }
}

private class KtReplaceGuardFix(ifExpression: KtIfExpression) : PsiUpdateModCommandAction<KtIfExpression>(ifExpression) {

  override fun getFamilyName(): String {
    return DevKitBundle.message("inspections.control.flow.exception.manual.rethrow.fix.name")
  }

  override fun invoke(context: ActionContext, element: KtIfExpression, updater: ModPsiUpdater) {
    val thrown = thrownExpression(element) ?: return
    val replacement = KtPsiFactory(context.project()).createExpression("$RETHROW_CONTROL_FLOW_EXCEPTION_KOTLIN_FQN(${thrown.text})")
    val inserted = element.replace(replacement) as KtElement
    ShortenReferencesFacility.getInstance().shorten(inserted)
  }

  private fun thrownExpression(ifExpression: KtIfExpression): KtExpression? {
    val throwExpression = when (val then = ifExpression.then) {
      is KtThrowExpression -> then
      is KtBlockExpression -> then.statements.singleOrNull() as? KtThrowExpression
      else -> null
    }
    return throwExpression?.thrownExpression
  }
}
