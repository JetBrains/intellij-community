// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableSymbol
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class SelfAssignmentInspection : AbstractKotlinApplicableInspectionWithContext<KtBinaryExpression, String>(), CleanupLocalInspectionTool {
    override fun getProblemDescription(element: KtBinaryExpression, context: String): String =
        KotlinBundle.message("variable.0.is.assigned.to.itself", context)

    override fun apply(element: KtBinaryExpression, context: String, project: Project, updater: ModPsiUpdater) = element.delete()

    override fun getActionFamilyName(): String = KotlinBundle.message("remove.self.assignment.fix.text")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtBinaryExpression> = ApplicabilityRanges.SELF

    context(KtAnalysisSession)
    override fun prepareContext(element: KtBinaryExpression): String? {
        val left = element.left
        val right = element.right

        val leftResolvedCall = left?.resolveCall()?.singleVariableAccessCall()
        val callee = leftResolvedCall?.symbol ?: return null

        val rightResolvedCall = right?.resolveCall()?.singleVariableAccessCall()
        if (rightResolvedCall?.symbol != callee) return null

        if (callee !is KtVariableSymbol || callee.isVal) return null
        if (callee is KtPropertySymbol && (callee.modality != Modality.FINAL ||
                    callee.getter?.isDefault == false || callee.setter?.isDefault == false)) return null

        // Only check the dispatch receiver - properties with default accessors cannot have extension receivers.
        val leftReceiver = leftResolvedCall.partiallyAppliedSymbol.dispatchReceiver
        val rightReceiver = rightResolvedCall.partiallyAppliedSymbol.dispatchReceiver

        if (leftReceiver != null || rightReceiver != null) {
            // If the symbol is null, receiver expression's value might be unstable
            val leftReceiverSymbol = leftReceiver?.resolveToSymbol() ?: return null
            val rightReceiverSymbol = rightReceiver?.resolveToSymbol() ?: return null
            if (leftReceiverSymbol != rightReceiverSymbol) return null
        }

        return callee.name.asString()
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        if (element.operationToken != KtTokens.EQ) return false
        val leftRefExpr = element.left?.asNameReferenceExpression() ?: return false
        val rightRefExpr = element.right?.asNameReferenceExpression() ?: return false

        return leftRefExpr.text == rightRefExpr.text
    }

    private fun KtExpression.asNameReferenceExpression(): KtNameReferenceExpression? = when (this) {
        is KtNameReferenceExpression -> this
        is KtDotQualifiedExpression -> (selectorExpression as? KtNameReferenceExpression)
        else -> null
    }

    context(KtAnalysisSession)
    private fun KtReceiverValue.resolveToSymbol(): KtSymbol? = when (this) {
        is KtSmartCastedReceiverValue -> original.resolveToSymbol()
        is KtImplicitReceiverValue -> symbol
        is KtExplicitReceiverValue -> when (val receiverExpression = expression) {
            is KtThisExpression -> receiverExpression.instanceReference.mainReference.resolveToSymbol()
            is KtNameReferenceExpression -> receiverExpression.mainReference.resolveToSymbol()
            else -> null
        }
    }
}
