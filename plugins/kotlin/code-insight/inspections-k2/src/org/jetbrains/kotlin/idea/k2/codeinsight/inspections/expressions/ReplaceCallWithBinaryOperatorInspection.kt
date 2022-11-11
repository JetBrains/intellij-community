// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions

class ReplaceCallWithBinaryOperatorInspection : LocalInspectionTool() {

    @FileModifier.SafeTypeForPreview
    private data class Context(
        val operation: KtSingleValueToken,
        val isFloatingPointEquals: Boolean,
    )

    private class ReplaceCallWithOperatorFix(val context: Context) : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("replace.with.binary.operator")

        override fun getName(): String {
            // The `a == b` for Double/Float is IEEE 754 equality, so it might change the behavior.
            // In this case, we show a different quick fix message with 'INFORMATION' highlight type.
            if (context.isFloatingPointEquals) {
                return KotlinBundle.message("replace.total.order.equality.with.ieee.754.equality")
            }
            return KotlinBundle.message("replace.with.0", context.operation.value)
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtDotQualifiedExpression ?: return
            val receiver = element.receiverExpression
            val argument = element.callExpression?.singleArgumentExpression() ?: return

            val expressionToReplace = getReplacementTarget(element, context.operation) ?: return
            val factory = KtPsiFactory(project)
            val newExpression = factory.createExpressionByPattern("$0 ${context.operation.value} $1", receiver, argument, reformat = false)

            expressionToReplace.replace(newExpression)
        }

        private fun getReplacementTarget(element: KtDotQualifiedExpression, operation: KtSingleValueToken): KtExpression? {
            return when (operation) {
                KtTokens.EXCLEQ -> element.getWrappingPrefixExpressionOrNull()
                in OperatorConventions.COMPARISON_OPERATIONS -> element.parent as? KtBinaryExpression
                else -> element
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        dotQualifiedExpressionVisitor { element ->
            val context = getContextIfIsApplicable(element) ?: return@dotQualifiedExpressionVisitor
            holder.registerProblem(
                element,
                KotlinBundle.message("call.replaceable.with.binary.operator"),
                inspectionHighlightType(element, context),
                inspectionHighlightRangeInElement(element),
                ReplaceCallWithOperatorFix(context)
            )
        }

    private fun getContextIfIsApplicable(element: KtDotQualifiedExpression): Context? {
        val callExpression = element.selectorExpression as? KtCallExpression ?: return null
        val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return null
        val receiver = element.receiverExpression
        val argument = callExpression.singleArgumentExpression() ?: return null

        analyze(element) {
            val resolvedCall = callExpression.resolveCall().successfulFunctionCallOrNull() ?: return null
            if (resolvedCall.symbol.valueParameters.size != 1) return null
            if (resolvedCall.typeArgumentsMapping.isNotEmpty()) return null
            if (!element.isReceiverExpressionWithValue()) return null

            val operationToken = getOperationToken(calleeExpression) ?: return null
            val isFloatingPointEquals =
                operationToken == KtTokens.EQEQ && receiver.hasDoubleOrFloatType() && argument.hasDoubleOrFloatType()
            return Context(operationToken, isFloatingPointEquals)
        }
    }

    private fun inspectionHighlightType(element: KtDotQualifiedExpression, context: Context): ProblemHighlightType {
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

    private fun inspectionHighlightRangeInElement(element: KtDotQualifiedExpression): TextRange? =
        element.callExpression?.calleeExpression?.textRangeIn(element)

    context(KtAnalysisSession)
    private fun KtQualifiedExpression.isReceiverExpressionWithValue(): Boolean {
        val receiver = receiverExpression
        if (receiver is KtSuperExpression) return false
        return receiver.getKtType() != null
    }

    context(KtAnalysisSession)
    private fun getOperationToken(calleeExpression: KtSimpleNameExpression): KtSingleValueToken? {
        val identifier = calleeExpression.getReferencedNameAsName()
        val dotQualified = calleeExpression.parent.parent as? KtDotQualifiedExpression ?: return null
        fun isOperatorOrCompatible(): Boolean {
            val functionCall = calleeExpression.resolveCall()?.successfulFunctionCallOrNull()
            return (functionCall?.symbol as? KtFunctionSymbol)?.isOperator == true
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
                val comparedToZero = when {
                    binaryParent.right?.isZeroIntegerConstant() == true -> binaryParent.left
                    binaryParent.left?.isZeroIntegerConstant() == true -> binaryParent.right
                    else -> return null
                }
                if (comparedToZero != dotQualified) return null

                val token = binaryParent.operationToken as? KtSingleValueToken ?: return null
                if (token in OperatorConventions.COMPARISON_OPERATIONS) {
                    if (comparedToZero == binaryParent.left) token else token.invertedComparison()
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

context(KtAnalysisSession)
private fun KtCallableSymbol.isAnyEquals(): Boolean {
    val overriddenSymbols = sequence {
        yield(this@isAnyEquals)
        yieldAll(this@isAnyEquals.getAllOverriddenSymbols())
    }
    return overriddenSymbols.any { it.callableIdIfNonLocal == KOTLIN_ANY_EQUALS_CALLABLE_ID }
}

context(KtAnalysisSession)
private fun KtExpression.isAnyEquals(): Boolean {
    val resolvedCall = resolveCall()?.successfulCallOrNull<KtSimpleFunctionCall>() ?: return false
    return resolvedCall.symbol.isAnyEquals()
}


/**
 * This function tries to determine when `first == second` expression is considered valid.
 * According to Kotlin language specification, “no two objects unrelated by subtyping can ever be considered equal by ==”.
 * [8.9.2 Value equality expressions](https://kotlinlang.org/spec/expressions.html#value-equality-expressions)
 */
context(KtAnalysisSession)
private fun areRelatedBySubtyping(first: KtExpression, second: KtExpression): Boolean {
    val firstType = first.getKtType() ?: return false
    val secondType = second.getKtType() ?: return false
    return firstType isSubTypeOf secondType || secondType isSubTypeOf firstType
}

context(KtAnalysisSession)
private fun KtExpression.hasDoubleOrFloatType(): Boolean {
    val type = getKtType() ?: return false
    return type isSubTypeOf builtinTypes.DOUBLE || type isSubTypeOf builtinTypes.FLOAT
}

context(KtAnalysisSession)
private fun KtExpression.hasUnknownNullabilityType(): Boolean {
    return this.getKtType()?.nullability == KtTypeNullability.UNKNOWN
}
