// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("InsertExplicitTypeArgumentsUtils")

package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.isDenotable
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.types.Variance

context(_: KaSession)
@OptIn(KaExperimentalApi::class, KaContextParameterApi::class)
fun getRenderedTypeArguments(element: KtCallElement): String? {
    val resolvedCall = element.resolveToCall()?.singleFunctionCallOrNull() ?: return null
    val typeParameterSymbols = resolvedCall.partiallyAppliedSymbol.symbol.typeParameters
    if (typeParameterSymbols.isEmpty()) return null
    val renderedTypeParameters = buildList {
        for (symbol in typeParameterSymbols) {
            val type = resolvedCall.typeArgumentsMapping[symbol] ?: return null

            /** Can't use definitely-non-nullable type as reified type argument */
            if (type is KaDefinitelyNotNullType && symbol.isReified) return null

            if (type.containsErrorType() || !(type.isDenotable || type is KaFlexibleType)) return null
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

context(_: KaSession)
private fun KaType.containsErrorType(): Boolean = when (this) {
    is KaErrorType -> true
    is KaFunctionType -> {
        (receiverType?.containsErrorType() == true)
                || returnType.containsErrorType()
                || parameterTypes.any { it.containsErrorType() }
                || typeArguments.any { it.type?.containsErrorType() == true }
    }

    is KaClassType -> typeArguments.any { it.type?.containsErrorType() == true }
    is KaDefinitelyNotNullType -> original.containsErrorType()
    is KaFlexibleType -> lowerBound.containsErrorType() || upperBound.containsErrorType()
    is KaIntersectionType -> conjuncts.any { it.containsErrorType() }
    is KaTypeParameterType, is KaCapturedType, is KaDynamicType -> false
    else -> false
}
