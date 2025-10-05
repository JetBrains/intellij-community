// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.psi.KtElement

object TypeParameterUtils {
    context(_: KaSession)
    fun returnTypeOfCallDependsOnTypeParameters(callElement: KtElement): Boolean {
        return collectTypeParametersOnWhichReturnTypeDepends(callElement).isNotEmpty()
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    fun collectTypeParametersOnWhichReturnTypeDepends(callElement: KtElement): Set<KaTypeParameterSymbol> {
        val call = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return emptySet()
        val callSymbol = call.partiallyAppliedSymbol.symbol

        @OptIn(KaExperimentalApi::class)
        val typeParameters = callSymbol.typeParameters
        val returnType = callSymbol.returnType
        return typeParameters.filter { typeReferencesTypeParameter(it, returnType) }.toSet()
    }

    context(_: KaSession)
    fun typeReferencesTypeParameter(typeParameter: KaTypeParameterSymbol, type: KaType): Boolean {
        return when (type) {
            is KaTypeParameterType -> type.symbol == typeParameter
            is KaClassType -> type.typeArguments.mapNotNull { it.type }.any { typeReferencesTypeParameter(typeParameter, it) }
            else -> false
        }
    }
}