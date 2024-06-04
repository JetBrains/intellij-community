// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.psi.KtElement

object TypeParameterUtils {
    context(KtAnalysisSession)
    fun returnTypeOfCallDependsOnTypeParameters(callElement: KtElement): Boolean {
        return collectTypeParametersOnWhichReturnTypeDepends(callElement).isNotEmpty()
    }

    context(KtAnalysisSession)
    fun collectTypeParametersOnWhichReturnTypeDepends(callElement: KtElement): Set<KtTypeParameterSymbol> {
        val call = callElement.resolveCallOld()?.singleFunctionCallOrNull() ?: return emptySet()
        val callSymbol = call.partiallyAppliedSymbol.symbol
        val typeParameters = callSymbol.typeParameters
        val returnType = callSymbol.returnType
        return typeParameters.filter { typeReferencesTypeParameter(it, returnType) }.toSet()
    }

    context(KtAnalysisSession)
    fun typeReferencesTypeParameter(typeParameter: KtTypeParameterSymbol, type: KtType): Boolean {
        return when (type) {
            is KtTypeParameterType -> type.symbol == typeParameter
            is KtNonErrorClassType -> type.ownTypeArguments.mapNotNull { it.type }.any { typeReferencesTypeParameter(typeParameter, it) }
            else -> false
        }
    }
}