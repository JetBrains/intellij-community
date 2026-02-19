// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isEqualsMethodSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class RecursiveEqualsCallInspection : KotlinApplicableInspectionBase.Simple<KtExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ) = object : KtVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(element: KtExpression, context: Unit): String =
        KotlinBundle.message("recursive.equals.call")

    override fun createQuickFix(
        element: KtExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtExpression> {
        val invert = element is KtBinaryExpression && element.operationToken == KtTokens.EXCLEQ
        return ReplaceWithReferentialEqualityFix(invert)
    }

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        return when (element) {
            is KtBinaryExpression -> {
                // Quick check for == or != operators
                (element.operationToken == KtTokens.EQEQ || element.operationToken == KtTokens.EXCLEQ) &&
                        element.right is KtNameReferenceExpression &&
                        element.getNonStrictParentOfType<KtNamedFunction>() != null
            }

            is KtCallExpression -> {
                // Quick check for equals() calls
                val calleeExpression = element.calleeExpression as? KtSimpleNameExpression
                calleeExpression?.getReferencedNameAsName() == OperatorNameConventions.EQUALS &&
                        element.valueArguments.singleOrNull()?.getArgumentExpression() is KtNameReferenceExpression &&
                        element.getNonStrictParentOfType<KtNamedFunction>() != null
            }

            else -> false
        }
    }

    override fun KaSession.prepareContext(element: KtExpression): Unit? {
        val argumentExpr = when (element) {
            is KtBinaryExpression -> element.right
            is KtCallExpression -> element.valueArguments.singleOrNull()?.getArgumentExpression()
            else -> return null
        } as? KtNameReferenceExpression ?: return null

        val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val calledSymbol = call.symbol as? KaNamedFunctionSymbol ?: return null
        val dispatchReceiver = call.partiallyAppliedSymbol.dispatchReceiver ?: return null

        if (!calledSymbol.isEqualsMethodSymbol()) return null

        val containingFunction = element.getNonStrictParentOfType<KtNamedFunction>() ?: return null
        val containingSymbol = containingFunction.symbol as? KaNamedFunctionSymbol ?: return null

        if (calledSymbol != containingSymbol) return null
        // Check if the dispatch receiver is a call on "this" instance
        val isThisReceiver = when (dispatchReceiver) {
            is KaImplicitReceiverValue -> {
                // Implicit receiver - this is a call on "this" 
                val receiverClassSymbol = dispatchReceiver.symbol as? KaClassSymbol
                val containingClassSymbol = containingSymbol.containingSymbol as? KaClassSymbol
                receiverClassSymbol != null && receiverClassSymbol == containingClassSymbol
            }

            is KaExplicitReceiverValue -> {
                // Explicit receiver - check if it's specifically "this"
                dispatchReceiver.expression is KtThisExpression
            }

            else -> false
        }
        if (!isThisReceiver) return null

        val argumentSymbol = argumentExpr.mainReference.resolveToSymbol() as? KaValueParameterSymbol ?: return null
        val parameterSymbol = containingSymbol.valueParameters.singleOrNull() ?: return null
        return (argumentSymbol == parameterSymbol).asUnit
    }
}

private class ReplaceWithReferentialEqualityFix(invert: Boolean) : KotlinModCommandQuickFix<KtExpression>() {
    private val operator = if (invert) "!==" else "==="

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.0", operator)

    override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
        val (right, target) = when (element) {
            is KtBinaryExpression -> {
                element.right to element
            }

            is KtCallExpression -> with(element) {
                valueArguments.firstOrNull()?.getArgumentExpression() to getQualifiedExpressionForSelectorOrThis()
            }
            else -> return
        }
        if (right == null) return
        target.replace(KtPsiFactory(project).createExpressionByPattern("this $0 $1", operator, right))
    }
}
