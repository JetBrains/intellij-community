// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class RedundantLambdaArrowInspection : KotlinApplicableInspectionBase.Simple<KtLambdaExpression, Unit>() {

    override fun getProblemDescription(
        element: KtLambdaExpression,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("redundant.lambda.arrow")

    override fun getApplicableRanges(element: KtLambdaExpression): List<TextRange> {
        val functionLiteral = element.functionLiteral
        val arrow = functionLiteral.arrow ?: return emptyList()
        val singleParameter = functionLiteral.valueParameters.singleOrNull()
        return listOf(TextRange(singleParameter?.startOffset ?: arrow.startOffset, arrow.endOffset).shiftLeft(element.startOffset))
    }

    override fun isApplicableByPsi(element: KtLambdaExpression): Boolean {
        val functionLiteral = element.functionLiteral
        if (functionLiteral.arrow == null) return false
        val parameters = functionLiteral.valueParameters
        val singleParameter = parameters.singleOrNull()
        if (singleParameter?.typeReference != null) return false
        if (parameters.isNotEmpty() && singleParameter?.nameAsName != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) {
            return false
        }

        if (element.getStrictParentOfType<KtWhenEntry>()?.expression == element) return false
        if (element.getStrictParentOfType<KtContainerNodeForControlStructureBody>()?.expression == element) return false

        val callExpression = element.parent?.parent as? KtCallExpression
        if (callExpression != null) {
            val callee = callExpression.calleeExpression as? KtNameReferenceExpression
            if (callee != null && callee.getReferencedName() == "forEach" && singleParameter?.nameAsName != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return false
        }

        return true
    }

    override fun KaSession.prepareContext(element: KtLambdaExpression): Unit? {
        val functionLiteral = element.functionLiteral
        val parameters = functionLiteral.valueParameters
        if (parameters.isNotEmpty() && element.expectedType == null) return null

        val valueArgument = element.getStrictParentOfType<KtValueArgument>()
        val valueArgumentCalls = valueArgument?.parentCallExpressions().orEmpty()
        val topLevelCall = valueArgumentCalls.lastOrNull()
        if (topLevelCall != null && !topLevelCall.nestedCallsAreUnchanged(element)) return null

        val functionLiteralSymbol = functionLiteral.symbol
        return (!functionLiteral.anyDescendantOfType<KtNameReferenceExpression> {
                it.text == StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier && it.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol?.containingDeclaration != functionLiteralSymbol
            }).asUnit
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> {
        return lambdaExpressionVisitor(fun(lambdaExpression: KtLambdaExpression) {
            visitTargetElement(lambdaExpression, holder, isOnTheFly)
        })
    }

    override fun createQuickFix(
        element: KtLambdaExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtLambdaExpression> = object : KotlinModCommandQuickFix<KtLambdaExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("delete.fix.family.name")

        override fun applyFix(
            project: Project,
            element: KtLambdaExpression,
            updater: ModPsiUpdater
        ) {
            val functionLiteral = element.functionLiteral
            functionLiteral.valueParameterList?.delete()
            functionLiteral.arrow?.delete()
        }
    }
}

private fun KtCallExpression.nestedCallsAreUnchanged(lambdaExpression: KtLambdaExpression): Boolean {
    val bodyStart = lambdaExpression.bodyExpression!!.startOffsetInParent
    val qualifiedExpression = parent as? KtQualifiedExpression
    val fullExpr = if (qualifiedExpression != null && qualifiedExpression.selectorExpression == this) { qualifiedExpression } else { this }
    val offset = lambdaExpression.textOffset - fullExpr.textOffset
    val fragmentWithoutArrow = KtPsiFactory(project).createExpressionCodeFragment(
        fullExpr.text.substring(0, offset) // everything before lambda
                + "{" + fullExpr.text.substring(offset + bodyStart) // lambda without parameters and further
        , this).getContentElement() ?: return false

    val resolveResults = mutableListOf<PsiElement>()
    analyze(fragmentWithoutArrow) {
        fragmentWithoutArrow.forEachDescendantOfType<KtCallExpression> {
            resolveResults.addIfNotNull(it.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol?.psi)
        }
    }

    val originalResults = mutableListOf<PsiElement>()
    analyze(this) {
        fullExpr.forEachDescendantOfType<KtCallExpression> {
            originalResults.addIfNotNull(it.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol?.psi)
        }
    }

    return resolveResults == originalResults
}

private fun KtValueArgument.parentCallExpressions(): List<KtCallExpression> {
    val calls = mutableListOf<KtCallExpression>()
    var argument = this
    while (true) {
        val call = argument.getStrictParentOfType<KtCallExpression>() ?: break
        calls.add(call)
        argument = call.getStrictParentOfType() ?: break
    }
    return calls
}
