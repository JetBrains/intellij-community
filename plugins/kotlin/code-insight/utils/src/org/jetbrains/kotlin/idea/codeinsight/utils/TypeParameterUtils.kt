// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.resolution.KtResolvableCall

object TypeParameterUtils {
    context(_: KaSession)
    fun returnTypeOfCallDependsOnTypeParameters(callElement: KtResolvableCall): Boolean {
        return collectTypeParametersOnWhichReturnTypeDepends(callElement).isNotEmpty()
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    fun collectTypeParametersOnWhichReturnTypeDepends(callElement: KtResolvableCall): Set<KaTypeParameterSymbol> {
        val call = callElement.tryResolveCall()?.single?.function ?: return emptySet()
        val callSymbol = call.symbol

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