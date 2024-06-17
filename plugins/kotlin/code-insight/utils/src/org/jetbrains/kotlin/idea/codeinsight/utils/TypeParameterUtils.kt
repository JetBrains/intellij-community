// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.psi.KtElement

object TypeParameterUtils {
    context(KaSession)
    fun returnTypeOfCallDependsOnTypeParameters(callElement: KtElement): Boolean {
        return collectTypeParametersOnWhichReturnTypeDepends(callElement).isNotEmpty()
    }

    context(KaSession)
    fun collectTypeParametersOnWhichReturnTypeDepends(callElement: KtElement): Set<KaTypeParameterSymbol> {
        val call = callElement.resolveCallOld()?.singleFunctionCallOrNull() ?: return emptySet()
        val callSymbol = call.partiallyAppliedSymbol.symbol
        val typeParameters = callSymbol.typeParameters
        val returnType = callSymbol.returnType
        return typeParameters.filter { typeReferencesTypeParameter(it, returnType) }.toSet()
    }

    context(KaSession)
    fun typeReferencesTypeParameter(typeParameter: KaTypeParameterSymbol, type: KtType): Boolean {
        return when (type) {
            is KtTypeParameterType -> type.symbol == typeParameter
            is KtNonErrorClassType -> type.ownTypeArguments.mapNotNull { it.type }.any { typeReferencesTypeParameter(typeParameter, it) }
            else -> false
        }
    }
}