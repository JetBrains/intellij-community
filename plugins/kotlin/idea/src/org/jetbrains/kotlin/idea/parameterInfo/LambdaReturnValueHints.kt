// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetails
import org.jetbrains.kotlin.idea.codeInsight.hints.PsiInlayInfoDetail
import org.jetbrains.kotlin.idea.codeInsight.hints.TextInlayInfoDetail
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext.USED_AS_RESULT_OF_LAMBDA
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

fun KtExpression.isLambdaReturnValueHintsApplicable(): Boolean {
    if (this is KtWhenExpression || this is KtBlockExpression) {
        return false
    }

    if (this is KtIfExpression && !this.isOneLiner()) {
        return false
    }

    if (this.getParentOfType<KtIfExpression>(true)?.isOneLiner() == true) {
        return false
    }

    if (!KtPsiUtil.isStatement(this)) {
        if (!allowLabelOnExpressionPart(this)) {
            return false
        }
    } else if (forceLabelOnExpressionPart(this)) {
        return false
    }
    val functionLiteral = this.getParentOfType<KtFunctionLiteral>(true)
    val body = functionLiteral?.bodyExpression ?: return false
    if (body.statements.size == 1 && body.statements[0] == this) {
        return false
    }

    return true
}

fun provideLambdaReturnValueHints(expression: KtExpression): InlayInfoDetails? {
    if (!expression.isLambdaReturnValueHintsApplicable()) {
        return null
    }

    val bindingContext = expression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL_WITH_CFA)
    if (bindingContext[USED_AS_RESULT_OF_LAMBDA, expression] == true) {
        val lambdaExpression = expression.getStrictParentOfType<KtLambdaExpression>() ?: return null
        val lambdaName = lambdaExpression.getNameOfFunctionThatTakesLambda() ?: "lambda"
        val inlayInfo = InlayInfo("", expression.endOffset)
        return InlayInfoDetails(inlayInfo, listOf(TextInlayInfoDetail("^"), PsiInlayInfoDetail(lambdaName, lambdaExpression)))
    }
    return null
}

private fun KtLambdaExpression.getNameOfFunctionThatTakesLambda(): String? {
    val lambda = this
    val callExpression = this.getStrictParentOfType<KtCallExpression>() ?: return null
    if (callExpression.lambdaArguments.any { it.getLambdaExpression() == lambda }) {
        val parent = lambda.parent
        if (parent is KtLabeledExpression) {
            return parent.getLabelName()
        }
        return (callExpression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
    }
    return null
}

private fun allowLabelOnExpressionPart(expression: KtExpression): Boolean {
    val parent = expression.parent as? KtExpression ?: return false
    return expression == expressionStatementPart(parent)
}

private fun forceLabelOnExpressionPart(expression: KtExpression): Boolean {
    return expressionStatementPart(expression) != null
}

private fun expressionStatementPart(expression: KtExpression): KtExpression? {
    val splitPart: KtExpression = when (expression) {
        is KtAnnotatedExpression -> expression.baseExpression
        is KtLabeledExpression -> expression.baseExpression
        else -> null
    } ?: return null

    if (!isNewLineBeforeExpression(splitPart)) {
        return null
    }

    return splitPart
}

private fun isNewLineBeforeExpression(expression: KtExpression): Boolean {
    val whiteSpace = expression.node.treePrev?.psi as? PsiWhiteSpace ?: return false
    return whiteSpace.text.contains("\n")
}
