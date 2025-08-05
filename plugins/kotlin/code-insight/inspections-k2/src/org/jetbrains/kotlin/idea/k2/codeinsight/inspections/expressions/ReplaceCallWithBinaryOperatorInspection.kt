// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.builtinTypes
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class ReplaceCallWithBinaryOperatorInspection :
    KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, ReplaceCallWithBinaryOperatorInspection.Context>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    @FileModifier.SafeTypeForPreview
    data class Context(val operation: KtSingleValueToken, val isFloatingPointEquals: Boolean)

    override fun getProblemDescription(element: KtDotQualifiedExpression, context: Context) =
        KotlinBundle.message("call.replaceable.with.binary.operator")

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.callExpression?.calleeExpression }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        if (element.receiverExpression is KtSuperExpression) return false
        val callExpression = element.selectorExpression as? KtCallExpression ?: return false
        if (callExpression.valueArguments.size != 1) return false
        val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return false
        val identifier = calleeExpression.getReferencedNameAsName()
        return (identifier == OperatorNameConventions.EQUALS
                || identifier == OperatorNameConventions.COMPARE_TO
                || identifier in OperatorNameConventions.BINARY_OPERATION_NAMES)
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Context? {
        val callExpression = element.selectorExpression as? KtCallExpression ?: return null
        val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return null
        val receiver = element.receiverExpression
        val argument = callExpression.singleArgumentExpression() ?: return null

        analyze(element) {
            val resolvedCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
            if (resolvedCall.symbol.valueParameters.size != 1) return null
            if (resolvedCall.typeArgumentsMapping.isNotEmpty()) return null
            if (!element.isReceiverExpressionWithValue()) return null

            val operationToken = getOperationToken(calleeExpression) ?: return null
            val isFloatingPointEquals =
                operationToken == KtTokens.EQEQ && receiver.hasDoubleOrFloatType() && argument.hasDoubleOrFloatType()
            return Context(operationToken, isFloatingPointEquals)
        }
    }

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("replace.with.binary.operator")

        // The `a == b` for Double/Float is IEEE 754 equality, so it might change the behavior.
        // In this case, we show a different quick fix message with 'INFORMATION' highlight type.
        override fun getName(): String =
            if (context.isFloatingPointEquals) KotlinBundle.message("replace.total.order.equality.with.ieee.754.equality")
            else KotlinBundle.message("replace.with.0", context.operation.value)

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            val receiver = element.receiverExpression
            val argument = element.callExpression?.singleArgumentExpression() ?: return

            val expressionToReplace = element.getReplacementTarget(context.operation) ?: return
            val factory = KtPsiFactory(project)
            val newExpression = factory.createExpressionByPattern("$0 ${context.operation.value} $1", receiver, argument, reformat = false)

            expressionToReplace.replace(newExpression)
        }
    }

    private fun KtDotQualifiedExpression.getReplacementTarget(operation: KtSingleValueToken): KtExpression? {
        return when (operation) {
            KtTokens.EXCLEQ -> this.getWrappingPrefixExpressionOrNull()
            in OperatorConventions.COMPARISON_OPERATIONS -> this.parent as? KtBinaryExpression
            else -> this
        }
    }

    override fun getProblemHighlightType(element: KtDotQualifiedExpression, context: Context): ProblemHighlightType {
        if (context.isFloatingPointEquals) {
            return ProblemHighlightType.INFORMATION
        }
        return when (context.operation) {
            KtTokens.EQEQ, KtTokens.EXCLEQ ->
                analyze(element) {
                    // When the receiver has flexible nullability, `a.equals(b)` is not strictly equivalent to `a == b`
                    // If `a` is null, then `a.equals(b)` throws an NPE, but `a == b` is safe
                    if (element.receiverExpression.hasUnknownNullabilityType()) {
                        ProblemHighlightType.INFORMATION
                    } else {
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    }
                }

            in OperatorConventions.COMPARISON_OPERATIONS -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            else -> ProblemHighlightType.INFORMATION
        }
    }

    context(_: KaSession)
    private fun KtQualifiedExpression.isReceiverExpressionWithValue(): Boolean {
        val receiver = receiverExpression
        if (receiver is KtSuperExpression) return false
        return receiver.expressionType != null
    }

    context(_: KaSession)
    private fun getOperationToken(calleeExpression: KtSimpleNameExpression): KtSingleValueToken? {
        val identifier = calleeExpression.getReferencedNameAsName()
        val dotQualified = calleeExpression.parent.parent as? KtDotQualifiedExpression ?: return null
        fun isOperatorOrCompatible(): Boolean {
            val functionCall = calleeExpression.resolveToCall()?.successfulFunctionCallOrNull()
            return (functionCall?.symbol as? KaNamedFunctionSymbol)?.isOperator == true
        }
        return when (identifier) {
            OperatorNameConventions.EQUALS -> {
                val receiver = dotQualified.receiverExpression
                val argument = dotQualified.callExpression?.singleArgumentExpression() ?: return null
                if (!dotQualified.isAnyEquals() || !areRelatedBySubtyping(receiver, argument)) return null

                val prefixExpression = dotQualified.getWrappingPrefixExpressionOrNull()
                if (prefixExpression?.operationToken == KtTokens.EXCL) KtTokens.EXCLEQ
                else KtTokens.EQEQ
            }

            OperatorNameConventions.COMPARE_TO -> {
                if (!isOperatorOrCompatible()) return null
                val binaryParent = dotQualified.parent as? KtBinaryExpression ?: return null

                val right = binaryParent.right ?: return null
                val left = binaryParent.left ?: return null

                val comparedToZero = when {
                    right.isZeroIntegerConstant -> left
                    left.isZeroIntegerConstant -> right
                    else -> return null
                }
                if (comparedToZero != dotQualified) return null

                val token = binaryParent.operationToken as? KtSingleValueToken ?: return null
                if (token in OperatorConventions.COMPARISON_OPERATIONS) {
                    if (comparedToZero == left) token else token.invertedComparison()
                } else {
                    null
                }
            }

            else -> {
                if (!isOperatorOrCompatible()) return null
                OperatorConventions.BINARY_OPERATION_NAMES.inverse()[identifier]
            }
        }
    }
}

private val KOTLIN_ANY_EQUALS_CALLABLE_ID = CallableId(StandardClassIds.Any, Name.identifier("equals"))

context(_: KaSession)
private fun KaCallableSymbol.isAnyEquals(): Boolean {
    return allOverriddenSymbolsWithSelf.any { it.callableId == KOTLIN_ANY_EQUALS_CALLABLE_ID }
}

context(_: KaSession)
private fun KtExpression.isAnyEquals(): Boolean {
    val resolvedCall = resolveToCall()?.successfulCallOrNull<KaSimpleFunctionCall>() ?: return false
    return resolvedCall.symbol.isAnyEquals()
}

/**
 * This function tries to determine when `first == second` expression is considered valid.
 * According to Kotlin language specification, “no two objects unrelated by subtyping can ever be considered equal by ==”.
 * [8.9.2 Value equality expressions](https://kotlinlang.org/spec/expressions.html#value-equality-expressions)
 */
context(_: KaSession)
private fun areRelatedBySubtyping(first: KtExpression, second: KtExpression): Boolean {
    val firstType = first.expressionType ?: return false
    val secondType = second.expressionType ?: return false
    return firstType.isSubtypeOf(secondType) || secondType.isSubtypeOf(firstType)
}

context(_: KaSession)
private fun KtExpression.hasDoubleOrFloatType(): Boolean {
    val type = expressionType ?: return false
    return type.isSubtypeOf(builtinTypes.double) || type.isSubtypeOf(builtinTypes.float)
}

context(_: KaSession)
private fun KtExpression.hasUnknownNullabilityType(): Boolean {
    return expressionType?.nullability == KaTypeNullability.UNKNOWN
}
