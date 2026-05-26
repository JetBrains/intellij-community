// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.codeInsight.Nullability
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.hasFlexibleNullability
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.tryResolveCall
import org.jetbrains.kotlin.analysis.api.components.typeCreator
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundArrayAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExperimentalApi
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.types.Variance

class ExpectedExpressionMatcher(val types: List<KaType>? = null, val nullability: Nullability? = null) {

    context(_: KaSession)
    fun match(candidateType: KaType): Boolean {
        if (types != null && types.none { candidateType.isSubtypeOf(it) }) {
            return false
        }
        if (nullability != null && nullability != candidateType.computeNullability()) {
            return false
        }
        return true
    }

    context(_: KaSession)
    private fun KaType.computeNullability(): Nullability {
        return when {
            hasFlexibleNullability -> Nullability.UNKNOWN
            isMarkedNullable -> Nullability.NULLABLE
            else -> Nullability.NOT_NULL
        }
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

    @OptIn(KaExperimentalApi::class, KtExperimentalApi::class)
    context(_: KaSession)
    private fun getForArrayAccessArgument(target: KtElement): ExpectedExpressionMatcher? {
        val containerNode = target.parent as? KtContainerNode ?: return null
        val arrayAccessExpression = (containerNode.parent as? KtArrayAccessExpression) ?: return null

        for (call in arrayAccessExpression.tryResolveCall()?.calls.orEmpty()) {
            if (call is KaFunctionCall<*>) {
                for ((argumentExpression, sig) in call.argumentMapping) {
                    if (argumentExpression == target) {
                        return ExpectedExpressionMatcher(types = listOf(sig.returnType))
                    }
                }
            } else if (call is KaCompoundArrayAccessCall) {
                val argumentIndex = call.indexArguments.indexOf(target)
                if (argumentIndex >= 0) {
                    val valueParameter = call.getterCall.signature.valueParameters.getOrNull(argumentIndex)
                    if (valueParameter != null) {
                        return ExpectedExpressionMatcher(types = listOf(valueParameter.returnType))
                    }
                }
            }
        }

        return null
    }

    @OptIn(KaExperimentalApi::class, KtExperimentalApi::class)
    context(_: KaSession)
    private fun getForArgument(callElement: KtCallElement, argument: ValueArgument): ExpectedExpressionMatcher? {
        for (call in callElement.tryResolveCall()?.calls.orEmpty()) {
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
                return ExpectedExpressionMatcher(nullability = Nullability.NULLABLE)
            }
        }

        return null
    }

    context(_: KaSession)
    private fun getForIf(target: KtElement): ExpectedExpressionMatcher? {
        val containerNode = target.parent as? KtContainerNode ?: return null
        val ifExpression = containerNode.parent as? KtIfExpression ?: return null

        if (target == ifExpression.condition) {
            @OptIn(KaExperimentalApi::class)
            return ExpectedExpressionMatcher(
                types = listOf(typeCreator.classType(KaStandardTypeClassIds.BOOLEAN)),
                nullability = Nullability.NOT_NULL
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

            @OptIn(KaExperimentalApi::class)
            val elementType = when {
                loopParameter != null && loopParameter.typeReference != null -> loopParameter.symbol.returnType
                else -> typeCreator.classType(KaStandardTypeClassIds.ANY)
            }

            fun constructType(classId: ClassId): KaType {
                @OptIn(KaExperimentalApi::class)
                return typeCreator.classType(classId) {
                    typeArgument(Variance.OUT_VARIANCE, elementType)
                }
            }

            val possibleTypes = listOf(
                constructType(KOTLIN_ITERABLE_CLASS_ID),
                constructType(KOTLIN_SEQUENCE_CLASS_ID),
                constructType(JAVA_STREAM_CLASS_ID),
                @OptIn(KaExperimentalApi::class)
                typeCreator.classType(KaStandardTypeClassIds.CHAR_SEQUENCE)
            )

            return ExpectedExpressionMatcher(types = possibleTypes)
        }

        return null
    }
}
