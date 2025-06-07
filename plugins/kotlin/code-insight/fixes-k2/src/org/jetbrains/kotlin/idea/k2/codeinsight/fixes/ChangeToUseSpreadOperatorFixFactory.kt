// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaCapturedType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object ChangeToUseSpreadOperatorFixFactory {

    val changeToUseSpreadOperatorFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        val element = diagnostic.psi as? KtReferenceExpression ?: return@ModCommandBased emptyList()
        val callExpression = element.getStrictParentOfType<KtCallExpression>() ?: return@ModCommandBased emptyList()
        val arrayElementType = diagnostic.actualType.arrayElementType?.unwrap() ?: return@ModCommandBased emptyList()
        val functionCall = (callExpression.resolveToCall() as? KaErrorCallInfo)?.candidateCalls?.singleOrNull() as? KaFunctionCall<*>
            ?: return@ModCommandBased emptyList()

        if (functionCall.argumentMapping[element]?.symbol?.isVararg != true &&
            functionCall.symbol.callableId?.asSingleFqName() != FqName("kotlin.collections.mapOf")
        ) {
            return@ModCommandBased emptyList()
        }

        val buildType = substituteErrorAndTypeParameterTypesWithStarTypeProjections(diagnostic.expectedType) ?: return@ModCommandBased emptyList()
        if (!arrayElementType.isSubtypeOf(buildType)) return@ModCommandBased emptyList()

        listOf(
            ChangeToUseSpreadOperatorFix(element)
        )
    }
}

private fun KaType.unwrap(): KaType {
    return (this as? KaCapturedType)?.projection?.type ?: this
}

/**
 * Substitute type parameter types in the given [type] with star type projections.
 *
 * For instance, given Pair<T, Pair<Int, U>>, the function returns Pair<*, Pair<Int, *>>.
 */
@OptIn(KaExperimentalApi::class)
private fun KaSession.substituteErrorAndTypeParameterTypesWithStarTypeProjections(type: KaType): KaType? {
    return when (type) {
        is KaClassType -> buildClassType(type.symbol) {
            type.typeArguments.mapNotNull { it.type }.forEach {
                if (it is KaTypeParameterType || it is KaErrorType) {
                    argument(buildStarTypeProjection())
                } else {
                    substituteErrorAndTypeParameterTypesWithStarTypeProjections(it)?.let { type ->
                        argument(type)
                    }
                }
            }
        }

        else -> null
    }
}
