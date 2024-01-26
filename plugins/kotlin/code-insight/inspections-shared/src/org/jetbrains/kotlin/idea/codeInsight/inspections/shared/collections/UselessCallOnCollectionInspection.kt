// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance

class UselessCallOnCollectionInspection : AbstractUselessCallInspection() {
    override val uselessFqNames = mapOf(
        "kotlin.collections.filterNotNull" to deleteConversion,
        "kotlin.sequences.filterNotNull" to deleteConversion,
        "kotlin.collections.filterIsInstance" to deleteConversion,
        "kotlin.sequences.filterIsInstance" to deleteConversion,
        "kotlin.collections.mapNotNull" to Conversion("map"),
        "kotlin.sequences.mapNotNull" to Conversion("map"),
        "kotlin.collections.mapNotNullTo" to Conversion("mapTo"),
        "kotlin.sequences.mapNotNullTo" to Conversion("mapTo"),
        "kotlin.collections.mapIndexedNotNull" to Conversion("mapIndexed"),
        "kotlin.sequences.mapIndexedNotNull" to Conversion("mapIndexed"),
        "kotlin.collections.mapIndexedNotNullTo" to Conversion("mapIndexedTo"),
        "kotlin.sequences.mapIndexedNotNullTo" to Conversion("mapIndexedTo")
    )

    override val uselessNames = uselessFqNames.keys.toShortNames()

    context(KtAnalysisSession)
    private fun KtExpression.isLambdaReturningNotNull(): Boolean {
        val expression = if (this is KtLabeledExpression) this.baseExpression else this
        if (expression !is KtLambdaExpression) return false
        return expression.bodyExpression?.getKtType()?.isMarkedNullable == false
    }

    context(KtAnalysisSession)
    private fun KtExpression.isMethodReferenceReturningNotNull(): Boolean {
        val type = getKtType() as? KtFunctionalType ?: return false
        return !type.returnType.isMarkedNullable
    }

    context(KtAnalysisSession)
    override fun QualifiedExpressionVisitor.suggestConversionIfNeeded(
        expression: KtQualifiedExpression,
        calleeExpression: KtExpression,
        conversion: Conversion
    ) {
        val receiverType = expression.receiverExpression.getKtType() as? KtNonErrorClassType ?:return
        val receiverTypeArgument = receiverType.ownTypeArguments.singleOrNull() ?: return
        val receiverTypeArgumentType = receiverTypeArgument.type ?: return
        val resolvedCall = expression.resolveCall()?.singleFunctionCallOrNull() ?: return
        if (calleeExpression.text == "filterIsInstance") {
            if (receiverTypeArgument is KtTypeArgumentWithVariance && receiverTypeArgument.variance == Variance.IN_VARIANCE) return
            val typeParameterDescriptor = resolvedCall.symbol.typeParameters.singleOrNull() ?: return
            val argumentType = resolvedCall.typeArgumentsMapping[typeParameterDescriptor] ?: return
            if (receiverTypeArgumentType is KtFlexibleType || !receiverTypeArgumentType.isSubTypeOf(argumentType)) return
        } else {
            // xxxNotNull
            if (receiverTypeArgumentType.isMarkedNullable) return
            if (calleeExpression.text != "filterNotNull") {
                // Check if there is a function argument
                resolvedCall.argumentMapping.toList().lastOrNull()?.first?.let { lastArgument ->
                    // We do not have a problem if the lambda argument might return null
                    if (!lastArgument.isMethodReferenceReturningNotNull() && !lastArgument.isLambdaReturningNotNull()) return
                    // Otherwise, the
                }
            }
        }

        val newName = conversion.replacementName
        if (newName != null) {
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

    context(KtAnalysisSession)
    private fun KtType.isList() = this.fullyExpandedType.isClassTypeWithClassId(ClassId.topLevel(StandardNames.FqNames.list))
}
