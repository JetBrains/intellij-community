// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DestructuringUtils")

package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtParameter

fun getParameterNames(declaration: KtDestructuringDeclaration): List<String>? {
    return analyze(declaration) {
        val type = getClassType(declaration) ?: return null
        if (type.nullability != KtTypeNullability.NON_NULLABLE) return null
        val classSymbol = type.expandedClassSymbol

        if (classSymbol is KtNamedClassOrObjectSymbol && classSymbol.isData) {
            val constructorSymbol = classSymbol.getDeclaredMemberScope()
                .getConstructors()
                .find { it.isPrimary }
                ?: return null

            constructorSymbol.valueParameters.map { it.name.asString() }
        } else null
    }
}

context(KtAnalysisSession)
private fun getClassType(declaration: KtDestructuringDeclaration): KtNonErrorClassType? {
    val initializer = declaration.initializer
    val parentAsParameter = declaration.parent as? KtParameter
    val type = when {
        initializer != null -> initializer.getKtType()
        parentAsParameter != null -> parentAsParameter.getParameterSymbol().returnType
        else -> null
    }
    return when (type) {
        is KtNonErrorClassType -> type
        is KtFlexibleType -> type.lowerBound as? KtNonErrorClassType
        else -> null
    }
}