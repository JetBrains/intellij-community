// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus
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
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance

// TODO: This class is currently only registered for K2 due to bugs in the
//  K1 implementation of the analysis API.
//  Once it is fixed, it should be used for both K1 and K2.
//  See: KT-65376
@ApiStatus.Internal
open class UselessCallOnCollectionInspection : AbstractUselessCallInspection() {
    override val conversions: List<QualifiedFunctionCallConversion> = listOf(
        UselessFilterConversion(topLevelCallableId("kotlin.collections", "filterNotNull")),
        UselessFilterConversion(topLevelCallableId("kotlin.sequences", "filterNotNull")),
        UselessFilterConversion(topLevelCallableId("kotlin.collections", "filterIsInstance")),
        UselessFilterConversion(topLevelCallableId("kotlin.sequences", "filterIsInstance")),

        UselessMapNotNullConversion(topLevelCallableId("kotlin.collections", "mapNotNull"), replacementName = "map"),
        UselessMapNotNullConversion(topLevelCallableId("kotlin.sequences", "mapNotNull"), replacementName = "map"),
        UselessMapNotNullConversion(topLevelCallableId("kotlin.collections", "mapNotNullTo"), replacementName = "mapTo"),
        UselessMapNotNullConversion(topLevelCallableId("kotlin.sequences", "mapNotNullTo"), replacementName = "mapTo"),
        UselessMapNotNullConversion(topLevelCallableId("kotlin.collections", "mapIndexedNotNull"), replacementName = "mapIndexed"),
        UselessMapNotNullConversion(topLevelCallableId("kotlin.sequences", "mapIndexedNotNull"), replacementName = "mapIndexed"),
        UselessMapNotNullConversion(topLevelCallableId("kotlin.collections", "mapIndexedNotNullTo"), replacementName = "mapIndexedTo"),
        UselessMapNotNullConversion(topLevelCallableId("kotlin.sequences", "mapIndexedNotNullTo"), replacementName = "mapIndexedTo")
    )

    protected inner class UselessFilterConversion(
        override val targetCallableId: CallableId,
    ) : QualifiedFunctionCallConversion {
        context(_: KaSession)
        override fun createProblemDescriptor(
            manager: InspectionManager,
            expression: KtQualifiedExpression,
            calleeExpression: KtExpression,
            isOnTheFly: Boolean
        ): ProblemDescriptor? {
            val receiverType = expression.receiverExpression.expressionType as? KaClassType ?: return null
            val receiverTypeArgument = receiverType.typeArguments.singleOrNull() ?: return null
            val receiverTypeArgumentType = receiverTypeArgument.type ?: return null
            val resolvedCall = expression.resolveToCall()?.singleFunctionCallOrNull() ?: return null
            val callableName = resolvedCall.symbol.callableId?.callableName?.asString() ?: return null
            if (callableName == "filterIsInstance") {
                if (receiverTypeArgument is KaTypeArgumentWithVariance && receiverTypeArgument.variance == Variance.IN_VARIANCE) return null
                @OptIn(KaExperimentalApi::class)
                val typeParameterDescriptor = resolvedCall.symbol.typeParameters.singleOrNull() ?: return null
                val argumentType = resolvedCall.typeArgumentsMapping[typeParameterDescriptor] ?: return null
                if (receiverTypeArgumentType is KaFlexibleType || !receiverTypeArgumentType.isSubtypeOf(argumentType)) return null
            } else {
                // xxxNotNull
                if (receiverTypeArgumentType.isNullable) return null
            }

            val fix = if (resolvedCall.symbol.returnType.isList() && !receiverType.isList()) {
                // TODO: This fix only works for cases when `toList()` function is available; make that logic explicit so it's safe to reuse
                ReplaceSelectorOfQualifiedExpressionFix("toList()")
            } else {
                RemoveUselessCallFix()
            }
            return manager.createProblemDescriptor(
                expression,
                TextRange(
                    expression.operationTokenNode.startOffset - expression.startOffset,
                    calleeExpression.endOffset - expression.startOffset
                ),
                KotlinBundle.message("redundant.call.on.collection.type"),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                isOnTheFly,
                fix
            )
        }
    }

    protected inner class UselessMapNotNullConversion(
        override val targetCallableId: CallableId,
        val replacementCallableId: CallableId,
    ) : QualifiedFunctionCallConversion {

        /**
         * Assumes that [replacementName] is a "sibling" of [targetCallableId].
         */
        constructor(
            targetCallableId: CallableId,
            replacementName: String,
        ) : this(
            targetCallableId,
            replacementCallableId = targetCallableId.copy(Name.identifier(replacementName))
        )

        private val replacementName: String
            get() = replacementCallableId.callableName.asString()

        context(_: KaSession)
        override fun createProblemDescriptor(
            manager: InspectionManager,
            expression: KtQualifiedExpression,
            calleeExpression: KtExpression,
            isOnTheFly: Boolean
        ): ProblemDescriptor? {
            val receiverType = expression.receiverExpression.expressionType as? KaClassType ?: return null
            val receiverTypeArgument = receiverType.typeArguments.singleOrNull() ?: return null
            val receiverTypeArgumentType = receiverTypeArgument.type ?: return null
            val resolvedCall = expression.resolveToCall()?.singleFunctionCallOrNull() ?: return null

            if (receiverTypeArgumentType.isNullable) return null
            // Check if there is a function argument
            resolvedCall.argumentMapping.toList().lastOrNull()?.first?.let { lastArgument ->
                // We do not have a problem if the lambda argument might return null
                if (!lastArgument.isMethodReferenceReturningNotNull() && !lastArgument.isLambdaReturningNotNull()) return null
                // Otherwise, the
            }

            // Do not suggest quick-fix to prevent capturing the name
            if (expression.isUsingLabelInScope(replacementName)) {
                return null
            }
            return manager.createProblemDescriptor(
                expression,
                TextRange(
                    expression.operationTokenNode.startOffset - expression.startOffset,
                    calleeExpression.endOffset - expression.startOffset
                ),
                KotlinBundle.message("call.on.collection.type.may.be.reduced"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                RenameUselessCallFix(replacementName, callableIdToImport = replacementCallableId)
            )
        }
    }

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
    private fun KaType.isList() = this.fullyExpandedType.isClassType(StandardClassIds.List)
}
