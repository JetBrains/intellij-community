// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.substring

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isSimplifiableTo
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.isPure
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

abstract class ReplaceSubstringInspection: KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, Unit>() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = dotQualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
    protected fun KaSession.resolvesToMethod(element: KtDotQualifiedExpression, fqMethodName: String): Boolean {
        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
        val callableId = resolvedCall.symbol.callableId?.asSingleFqName()
        return callableId?.asString() == fqMethodName
    }

    protected fun KaSession.isFirstArgumentZero(element: KtDotQualifiedExpression): Boolean {
        val firstArg = element.callExpression?.valueArguments?.getOrNull(0)?.getArgumentExpression() ?: return false
        val constantValue = firstArg.evaluate() ?: return false
        return constantValue.value == 0
    }

    protected fun KaSession.isIndexOfCall(expression: KtExpression?, expectedReceiver: KtExpression): Boolean {
        return expression is KtDotQualifiedExpression
               && resolvesToMethod(expression, "kotlin.text.indexOf")
               && expression.receiverExpression.isSimplifiableTo(expectedReceiver)
               && expression.callExpression!!.valueArguments.size == 1
    }

    protected fun isMethodCall(callExpression: KtCallExpression?, name: String): Boolean {
        val calleeExpression = callExpression?.calleeExpression as? KtNameReferenceExpression ?: return false
        return calleeExpression.getReferencedName() == name
    }

    protected fun isSubstringFromZero(callExpression: KtCallExpression): Boolean {
        if (!isMethodCall(callExpression, "substring")) return false

        val arguments = callExpression.valueArguments
        if (arguments.size != 2) return false

        val firstArg = arguments[0].getArgumentExpression() as? KtConstantExpression ?: return false
        return firstArg.text == "0"
    }

    protected fun getBinaryExpressionWithMinus(callExpression: KtCallExpression): KtBinaryExpression? {
        val arguments = callExpression.valueArguments
        if (arguments.size != 2) return null

        val secondArg = arguments[1].getArgumentExpression() as? KtBinaryExpression ?: return null
        if (secondArg.operationToken != KtTokens.MINUS) return null
        if (secondArg.right == null) return null

        return secondArg
    }

    protected fun isAccessedOnSameReceiver(
        binaryExpression: KtBinaryExpression, 
        expectedReceiver: KtExpression
    ): Boolean {
        val left = binaryExpression.left as? KtDotQualifiedExpression ?: return false

        if (!left.receiverExpression.isSimplifiableTo(expectedReceiver)) return false

        val selector = left.selectorExpression as? KtNameReferenceExpression ?: return false
        return selector.getReferencedName() == "length"
    }

    protected fun KaSession.prepareContextBase(element: KtDotQualifiedExpression): Boolean {
        if (!resolvesToMethod(element, "kotlin.text.substring")) return false
        if (!element.receiverExpression.isPure()) return false
        return true
    }
}