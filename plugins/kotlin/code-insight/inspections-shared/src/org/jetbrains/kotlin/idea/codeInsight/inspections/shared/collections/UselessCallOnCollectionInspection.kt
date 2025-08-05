// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.fullyExpandedType
import org.jetbrains.kotlin.analysis.api.components.isClassType
import org.jetbrains.kotlin.analysis.api.components.isNullable
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.targetSymbol
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance

// TODO: This class is currently only registered for K2 due to bugs in the
//  K1 implementation of the analysis API.
//  Once it is fixed, it should be used for both K1 and K2.
//  See: KT-65376
class UselessCallOnCollectionInspection : AbstractUselessCallInspection() {
    override val uselessFqNames = mapOf(
        topLevelCallableId("kotlin.collections", "filterNotNull") to Conversion.Delete,
        topLevelCallableId("kotlin.sequences", "filterNotNull") to Conversion.Delete,
        topLevelCallableId("kotlin.collections", "filterIsInstance") to Conversion.Delete,
        topLevelCallableId("kotlin.sequences", "filterIsInstance") to Conversion.Delete,
        topLevelCallableId("kotlin.collections", "mapNotNull") to Conversion.Replace("map"),
        topLevelCallableId("kotlin.sequences", "mapNotNull") to Conversion.Replace("map"),
        topLevelCallableId("kotlin.collections", "mapNotNullTo") to Conversion.Replace("mapTo"),
        topLevelCallableId("kotlin.sequences", "mapNotNullTo") to Conversion.Replace("mapTo"),
        topLevelCallableId("kotlin.collections", "mapIndexedNotNull") to Conversion.Replace("mapIndexed"),
        topLevelCallableId("kotlin.sequences", "mapIndexedNotNull") to Conversion.Replace("mapIndexed"),
        topLevelCallableId("kotlin.collections", "mapIndexedNotNullTo") to Conversion.Replace("mapIndexedTo"),
        topLevelCallableId("kotlin.sequences", "mapIndexedNotNullTo") to Conversion.Replace("mapIndexedTo")
    )

    override val uselessNames = uselessFqNames.keys.toShortNames()

    context(_: KaSession)
    private fun KtExpression.isLambdaReturningNotNull(): Boolean {
        val expression = if (this is KtLabeledExpression) this.baseExpression else this
        if (expression !is KtLambdaExpression) return false
        var labelledReturnReturnsNullable = false
        expression.bodyExpression?.forEachDescendantOfType<KtReturnExpression> { returnExpression ->
            val targetExpression = returnExpression.targetSymbol?.psi?.parent
            if (targetExpression == expression) {
                labelledReturnReturnsNullable = labelledReturnReturnsNullable ||
                        returnExpression.returnedExpression?.expressionType?.isNullable == true
            }
        }
        return !labelledReturnReturnsNullable && expression.bodyExpression?.expressionType?.isNullable == false
    }

    context(_: KaSession)
    private fun KtExpression.isMethodReferenceReturningNotNull(): Boolean {
        val type = expressionType as? KaFunctionType ?: return false
        return !type.returnType.isNullable
    }

    context(_: KaSession)
    override fun QualifiedExpressionVisitor.suggestConversionIfNeeded(
        expression: KtQualifiedExpression,
        calleeExpression: KtExpression,
        conversion: Conversion
    ) {
        val receiverType = expression.receiverExpression.expressionType as? KaClassType ?: return
        val receiverTypeArgument = receiverType.typeArguments.singleOrNull() ?: return
        val receiverTypeArgumentType = receiverTypeArgument.type ?: return
        val resolvedCall = expression.resolveToCall()?.singleFunctionCallOrNull() ?: return
        val callableName = resolvedCall.symbol.callableId?.callableName?.asString() ?: return
        if (callableName == "filterIsInstance") {
            if (receiverTypeArgument is KaTypeArgumentWithVariance && receiverTypeArgument.variance == Variance.IN_VARIANCE) return
            @OptIn(KaExperimentalApi::class)
            val typeParameterDescriptor = resolvedCall.symbol.typeParameters.singleOrNull() ?: return
            val argumentType = resolvedCall.typeArgumentsMapping[typeParameterDescriptor] ?: return
            if (receiverTypeArgumentType is KaFlexibleType || !receiverTypeArgumentType.isSubtypeOf(argumentType)) return
        } else {
            // xxxNotNull
            if (receiverTypeArgumentType.isNullable) return
            if (callableName != "filterNotNull") {
                // Check if there is a function argument
                resolvedCall.argumentMapping.toList().lastOrNull()?.first?.let { lastArgument ->
                    // We do not have a problem if the lambda argument might return null
                    if (!lastArgument.isMethodReferenceReturningNotNull() && !lastArgument.isLambdaReturningNotNull()) return
                    // Otherwise, the
                }
            }
        }

        val newName = (conversion as? Conversion.Replace)?.replacementName
        if (newName != null) {
            // Do not suggest quick-fix to prevent capturing the name
            if (expression.isUsingLabelInScope(newName)) {
                return
            }
            val descriptor = holder.manager.createProblemDescriptor(
                expression,
                TextRange(
                    expression.operationTokenNode.startOffset - expression.startOffset,
                    calleeExpression.endOffset - expression.startOffset
                ),
                KotlinBundle.message("call.on.collection.type.may.be.reduced"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                RenameUselessCallFix(newName)
            )
            holder.registerProblem(descriptor)
        } else {
            val fix = if (resolvedCall.symbol.returnType.isList() && !receiverType.isList()) {
                ReplaceSelectorOfQualifiedExpressionFix("toList()")
            } else {
                RemoveUselessCallFix()
            }
            val descriptor = holder.manager.createProblemDescriptor(
                expression,
                TextRange(
                    expression.operationTokenNode.startOffset - expression.startOffset,
                    calleeExpression.endOffset - expression.startOffset
                ),
                KotlinBundle.message("useless.call.on.collection.type"),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                isOnTheFly,
                fix
            )
            holder.registerProblem(descriptor)
        }
    }

    context(_: KaSession)
    private fun KaType.isList() = this.fullyExpandedType.isClassType(StandardClassIds.List)
}
