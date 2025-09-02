// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.binaryExpressionVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

class ReplaceWithIgnoreCaseEqualsInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
  ): KtVisitor<*, *> = binaryExpressionVisitor {
    visitTargetElement(it, holder, isOnTheFly)
  }

  private val caseConversionFunctionFqNames: Map<String, FqName> =
    listOf(FqName("kotlin.text.toUpperCase"),
           FqName("kotlin.text.toLowerCase"),
           FqName("kotlin.text.lowercase"),
           FqName("kotlin.text.uppercase")).associateBy { it.shortName().asString() }


  override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
    if (element.operationToken != KtTokens.EQEQ) return false

    val leftCall = element.left?.getCallExpressionIfCaseConversion() ?: return false
    val rightCall = element.right?.getCallExpressionIfCaseConversion() ?: return false

    val leftCalleeText = leftCall.calleeExpression?.text ?: return false
    val rightCalleeText = rightCall.calleeExpression?.text ?: return false

    if (leftCalleeText != rightCalleeText) return false

    return caseConversionFunctionFqNames[leftCalleeText] != null
  }

  override fun KaSession.prepareContext(element: KtBinaryExpression): Unit? {
    val leftCall = element.left?.getCallExpressionIfCaseConversion() ?: return null
    val rightCall = element.right?.getCallExpressionIfCaseConversion() ?: return null

    val leftCallFqName = leftCall.resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName() ?: return null
    val rightCallFqName = rightCall.resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName() ?: return null

    if (leftCallFqName != rightCallFqName) return null
    if (leftCallFqName !in caseConversionFunctionFqNames.values) return null

    return Unit
  }

  override fun getProblemDescription(
    element: KtBinaryExpression,
    context: Unit,
  ): @InspectionMessage String =
    KotlinBundle.message("inspection.replace.with.ignore.case.equals.display.name")

  override fun createQuickFix(
    element: KtBinaryExpression,
    context: Unit,
  ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
      KotlinBundle.message("replace.with.0", "equals(..., ignoreCase = true)")

    override fun applyFix(
      project: Project,
      element: KtBinaryExpression,
      updater: ModPsiUpdater,
    ) {
      val leftCall = element.left?.getCallExpressionIfCaseConversion() ?: return
      val rightCall = element.right?.getCallExpressionIfCaseConversion() ?: return

      val psiFactory = KtPsiFactory(project)
      val leftReceiver = leftCall.getQualifiedExpressionForSelector()?.receiverExpression
      val rightReceiver = rightCall.getQualifiedExpressionForSelector()?.receiverExpression ?: psiFactory.createThisExpression()

      val newExpression = if (leftReceiver != null) {
        psiFactory.createExpressionByPattern("$0.equals($1, ignoreCase = true)", leftReceiver, rightReceiver)
      }
      else {
        psiFactory.createExpressionByPattern("equals($0, ignoreCase = true)", rightReceiver)
      }

      element.replace(newExpression)
    }
  }

  private fun KtExpression.getCallExpressionIfCaseConversion(): KtCallExpression? {
    return (this as? KtQualifiedExpression)?.callExpression ?: this as? KtCallExpression
  }
}
