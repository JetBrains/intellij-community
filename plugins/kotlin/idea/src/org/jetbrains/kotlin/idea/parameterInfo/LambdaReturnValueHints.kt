// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetails
import org.jetbrains.kotlin.idea.codeInsight.hints.PsiInlayInfoDetail
import org.jetbrains.kotlin.idea.codeInsight.hints.TextInlayInfoDetail
import org.jetbrains.kotlin.idea.codeInsight.hints.declarative.SHOW_RETURN_EXPRESSIONS
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContext.USED_AS_RESULT_OF_LAMBDA
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun KtExpression.isLambdaReturnValueHintsApplicable(allowOneLiner: Boolean = false): Boolean {
    //if (allowOneLiner && this.isOneLiner()) {
    //    val literalWithBody = this is KtBlockExpression && isFunctionalLiteralWithBody()
    //    return literalWithBody
    //}

    if (this is KtWhenExpression) {
        return false
    }

    if (this is KtBlockExpression) {
        if (allowOneLiner && this.isOneLiner()) {
            return isFunctionalLiteralWithBody()
        }
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
    return isFunctionalLiteralWithBody()
}

private fun KtExpression.isFunctionalLiteralWithBody(): Boolean {
    val functionLiteral = this.getParentOfType<KtFunctionLiteral>(true)
    val body = functionLiteral?.bodyExpression ?: return false
    return !(body.statements.size == 1 && body.statements[0] == this)
}

fun provideLambdaReturnValueHints(expression: KtExpression): InlayInfoDetails? {
    if (!expression.isLambdaReturnValueHintsApplicable()) {
        return null
    }

    val bindingContext = expression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL_WITH_CFA)
    return if (bindingContext[USED_AS_RESULT_OF_LAMBDA, expression] == true) {
        val functionLiteral = expression.getStrictParentOfType<KtFunctionLiteral>() ?: return null
        val lambdaExpression = functionLiteral.getStrictParentOfType<KtLambdaExpression>() ?: return null

        val lambdaName = lambdaExpression.getNameOfFunctionThatTakesLambda() ?: "lambda"
        val inlayInfo = InlayInfo("", expression.endOffset)
        InlayInfoDetails(
            inlayInfo,
            listOf(TextInlayInfoDetail("^"), PsiInlayInfoDetail(lambdaName, lambdaExpression)),
            option = SHOW_RETURN_EXPRESSIONS
        )
    } else null
}

fun provideLambdaReturnTypeHints(expression: KtExpression): InlayInfoDetails? {
    if (!expression.isLambdaReturnValueHintsApplicable(allowOneLiner = true)) {
        return null
    }

    val bindingContext = expression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL_WITH_CFA)
    return if (bindingContext[USED_AS_RESULT_OF_LAMBDA, expression] == true) {
        val functionLiteral = expression.getStrictParentOfType<KtFunctionLiteral>() ?: return null
        val type = bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, functionLiteral).safeAs<FunctionDescriptor>()?.returnType ?: return null
        val inlayInfo = InlayInfo("", expression.endOffset)
        val infoDetails = buildList {
            add(TextInlayInfoDetail(": "))
            addAll(HintsTypeRenderer.getInlayHintsTypeRenderer(bindingContext, expression).renderTypeIntoInlayInfo(type))
        }
        InlayInfoDetails(inlayInfo, infoDetails)
    } else null
}

private fun KtLambdaExpression.getNameOfFunctionThatTakesLambda(): String? {
    val lambda = this
    val callExpression = this.getStrictParentOfType<KtCallExpression>() ?: return null
    return if (callExpression.lambdaArguments.any { it.getLambdaExpression() == lambda }) {
        val parent = lambda.parent
        if (parent is KtLabeledExpression) {
            parent.getLabelName()
        } else {
            callExpression.calleeExpression.safeAs<KtNameReferenceExpression>()?.getReferencedName()
        }
    } else null
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
