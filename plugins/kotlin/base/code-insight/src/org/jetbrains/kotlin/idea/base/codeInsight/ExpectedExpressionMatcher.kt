// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundArrayAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.types.Variance

class ExpectedExpressionMatcher(val types: List<KaType>? = null, val nullability: KaTypeNullability? = null) {

    context(_: KaSession)
    fun match(candidateType: KaType): Boolean {
        if (types != null && types.none { candidateType.isSubtypeOf(it) }) {
            return false
        }
        if (nullability != null && nullability != candidateType.nullability) {
            return false
        }
        return true
    }
}

object ExpectedExpressionMatcherProvider {
    context(_: KaSession)
    operator fun get(target: KtElement): ExpectedExpressionMatcher? {
        return getForElvis(target)
            ?: getForValueArgument(target)
            ?: getForLambdaArgument(target)
            ?: getForArrayAccessArgument(target)
            ?: getForComparison(target)
            ?: getForIf(target)
            ?: getForLoopRange(target)
    }

    context(_: KaSession)
private fun getForElvis(target: KtElement): ExpectedExpressionMatcher? {
        val elvisExpression = (target.parent as? KtBinaryExpression)
            ?.takeIf { it.operationToken == KtTokens.ELVIS }
            ?: return null

        if (target == elvisExpression.right) {
            val elvisMatcher = get(elvisExpression)
            if (elvisMatcher != null) {
                return elvisMatcher
            }

            val leftType = elvisExpression.left?.expressionType
            if (leftType != null) {
                return ExpectedExpressionMatcher(types = listOf(leftType))
            }
        }

        return null
    }

    context(_: KaSession)
    private fun getForValueArgument(target: KtElement): ExpectedExpressionMatcher? {
        val valueArgument = when (val parent = target.parent) {
            is KtValueArgument -> parent
            else -> parent?.parent as? KtValueArgument
        }

        if (valueArgument != null) {
            val valueArgumentList = valueArgument.parent as? KtValueArgumentList
            val callElement = valueArgumentList?.parent as? KtCallElement
            if (callElement != null) {
                getForArgument(callElement, valueArgument)
            }
        }

        return null
    }

    context(_: KaSession)
    private fun getForLambdaArgument(target: KtElement): ExpectedExpressionMatcher? {
        val lambdaArgument = target.parent as? KtLambdaArgument ?: return null
        val callExpression = lambdaArgument.parent as? KtCallExpression ?: return null

        if (callExpression.lambdaArguments.singleOrNull() == lambdaArgument) {
            return getForArgument(callExpression, lambdaArgument)
        }

        return null
    }

    context(_: KaSession)
    private fun getForArrayAccessArgument(target: KtElement): ExpectedExpressionMatcher? {
        val containerNode = target.parent as? KtContainerNode ?: return null
        val arrayAccessExpression = (containerNode.parent as? KtArrayAccessExpression) ?: return null

        for (call in arrayAccessExpression.resolveToCall()?.calls.orEmpty()) {
            if (call is KaFunctionCall<*>) {
                for ((argumentExpression, sig) in call.argumentMapping) {
                    if (argumentExpression == target) {
                        return ExpectedExpressionMatcher(types = listOf(sig.returnType))
                    }
                }
            } else if (call is KaCompoundArrayAccessCall) {
                val argumentIndex = call.indexArguments.indexOf(target)
                if (argumentIndex >= 0) {
                    val valueParameter = call.getPartiallyAppliedSymbol.signature.valueParameters.getOrNull(argumentIndex)
                    if (valueParameter != null) {
                        return ExpectedExpressionMatcher(types = listOf(valueParameter.returnType))
                    }
                }
            }
        }

        return null
    }

    context(_: KaSession)
    private fun getForArgument(callElement: KtCallElement, argument: ValueArgument): ExpectedExpressionMatcher? {
        for (call in callElement.resolveToCall()?.calls.orEmpty()) {
            if (call is KaFunctionCall<*>) {
                for ((argumentExpression, sig) in call.argumentMapping) {
                    if (argumentExpression == argument) {
                        return ExpectedExpressionMatcher(types = listOf(sig.returnType))
                    }
                }
            }
        }

        return null
    }

    private val comparisonOperators = setOf(KtTokens.EQEQ, KtTokens.EXCLEQ, KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ)

    context(_: KaSession)
    private fun getForComparison(target: KtElement): ExpectedExpressionMatcher? {
        val binaryOperation = (target.parent as? KtBinaryExpression)
            ?.takeIf { it.operationToken in comparisonOperators }
            ?: return null

        val otherOperand = when (target) {
            binaryOperation.left -> binaryOperation.right
            binaryOperation.right -> binaryOperation.left
            else -> null
        }

        if (otherOperand != null) {
            if (otherOperand.isNull()) {
                return ExpectedExpressionMatcher(nullability = KaTypeNullability.NULLABLE)
            }
        }

        return null
    }

    context(_: KaSession)
    private fun getForIf(target: KtElement): ExpectedExpressionMatcher? {
        val containerNode = target.parent as? KtContainerNode ?: return null
        val ifExpression = containerNode.parent as? KtIfExpression ?: return null

        if (target == ifExpression.condition) {
            return ExpectedExpressionMatcher(
                types = listOf(buildClassType(DefaultTypeClassIds.BOOLEAN)),
                nullability = KaTypeNullability.NON_NULLABLE
            )
        }

        if ((target == ifExpression.then && ifExpression.`else` != null) || (target == ifExpression.`else`)) {
            return get(ifExpression)
        }

        return null
    }

    private val KOTLIN_ITERABLE_CLASS_ID = ClassId.fromString("kotlin/collections/Iterable")
    private val KOTLIN_SEQUENCE_CLASS_ID = ClassId.fromString("kotlin/sequences/Sequence")
    private val JAVA_STREAM_CLASS_ID = ClassId.fromString("java/util/stream/Stream")

    context(_: KaSession)
    private fun getForLoopRange(target: KtElement): ExpectedExpressionMatcher? {
        val containerNode = target.parent as? KtContainerNode ?: return null
        val forExpression = containerNode.parent as? KtForExpression ?: return null

        if (target == forExpression.loopRange) {
            val loopParameter = forExpression.loopParameter

            val elementType = when {
                loopParameter != null && loopParameter.typeReference != null -> loopParameter.symbol.returnType
                else -> buildClassType(DefaultTypeClassIds.ANY)
            }

            fun constructType(classId: ClassId): KaType {
                return buildClassType(classId) {
                    argument(elementType, Variance.OUT_VARIANCE)
                }
            }

            val possibleTypes = listOf(
                constructType(KOTLIN_ITERABLE_CLASS_ID),
                constructType(KOTLIN_SEQUENCE_CLASS_ID),
                constructType(JAVA_STREAM_CLASS_ID),
                buildClassType(DefaultTypeClassIds.CHAR_SEQUENCE)
            )

            return ExpectedExpressionMatcher(types = possibleTypes)
        }

        return null
    }
}
