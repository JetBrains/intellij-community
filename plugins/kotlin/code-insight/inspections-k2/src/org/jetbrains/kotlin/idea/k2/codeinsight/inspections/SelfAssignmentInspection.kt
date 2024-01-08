// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

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
        val leftCallee = leftResolvedCall?.symbol ?: return null
        val rightResolvedCall = right?.resolveCall()?.singleVariableAccessCall()
        val rightCallee = rightResolvedCall?.symbol ?: return null

        if (leftCallee != rightCallee) return null

        val rightDeclaration = rightCallee.psi?.safeAs<KtVariableDeclaration>() ?: return null

        if (!rightDeclaration.isVar) return null
        if (rightDeclaration is KtProperty) {
            if (rightDeclaration.isOverridable()) return null
            if (rightDeclaration.accessors.any { !it.getPropertyAccessorSymbol().isDefault }) return null
        }

        if (left.receiver(leftCallee) != right.receiver(rightCallee)) return null

        return rightDeclaration.name
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
    private fun KtExpression.receiver(
        callSymbol: KtVariableLikeSymbol,
    ): KtSymbol? {
        when (val receiverExpression = (this as? KtDotQualifiedExpression)?.receiverExpression) {
            is KtThisExpression -> return receiverExpression.instanceReference.mainReference.resolveToSymbol()
            is KtNameReferenceExpression -> return receiverExpression.mainReference.resolveToSymbol()
        }

        return callSymbol.getContainingSymbol() as? KtClassOrObjectSymbol
    }
}
