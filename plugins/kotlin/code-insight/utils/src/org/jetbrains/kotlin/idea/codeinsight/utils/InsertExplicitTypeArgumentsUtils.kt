// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("InsertExplicitTypeArgumentsUtils")

package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.types.Variance

context(KaSession)
fun getRenderedTypeArguments(element: KtCallElement): String? {
    val resolvedCall = element.resolveCallOld()?.singleFunctionCallOrNull() ?: return null
    val typeParameterSymbols = resolvedCall.partiallyAppliedSymbol.symbol.typeParameters
    if (typeParameterSymbols.isEmpty()) return null
    val renderedTypeParameters = buildList {
        for (symbol in typeParameterSymbols) {
            val type = resolvedCall.typeArgumentsMapping[symbol] ?: return null

            /** Can't use definitely-non-nullable type as reified type argument */
            if (type is KtDefinitelyNotNullType && symbol.isReified) return null

            if (type.containsErrorType() || !(type.isDenotable || type is KtFlexibleType)) return null
            add(type.render(position = Variance.IN_VARIANCE))
        }
    }

    return renderedTypeParameters.joinToString(separator = ", ", prefix = "<", postfix = ">")
}

fun addTypeArguments(element: KtCallElement, context: String, project: Project) {
    val callee = element.calleeExpression ?: return
    val argumentList = KtPsiFactory(project).createTypeArguments(context)
    val newArgumentList = element.addAfter(argumentList, callee) as KtTypeArgumentList
    ShortenReferencesFacility.getInstance().shorten(newArgumentList)
}

context(KaSession)
private fun KtType.containsErrorType(): Boolean = when (this) {
    is KtErrorType -> true
    is KtFunctionalType -> {
        (receiverType?.containsErrorType() == true)
                || returnType.containsErrorType()
                || parameterTypes.any { it.containsErrorType() }
                || ownTypeArguments.any { it.type?.containsErrorType() == true }
    }

    is KtNonErrorClassType -> ownTypeArguments.any { it.type?.containsErrorType() == true }
    is KtDefinitelyNotNullType -> original.containsErrorType()
    is KtFlexibleType -> lowerBound.containsErrorType() || upperBound.containsErrorType()
    is KtIntersectionType -> conjuncts.any { it.containsErrorType() }
    is KtTypeParameterType, is KtCapturedType, is KtDynamicType -> false
}
