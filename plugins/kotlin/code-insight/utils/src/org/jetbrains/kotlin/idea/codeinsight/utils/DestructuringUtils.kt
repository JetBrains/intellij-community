// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DestructuringUtils")

package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter

fun getParameterNames(declaration: KtDestructuringDeclaration): List<String>? {
    return analyze(declaration) {
        val type = getClassType(declaration) ?: return null
        getParameterNames(type)
    }
}

fun getParameterNames(expression: KtExpression): List<String>? {
    return analyze(expression) {
        val type = toNonErrorClassType(expression.getKtType()) ?: return null
        getParameterNames(type)
    }
}

context(KtAnalysisSession)
private fun getParameterNames(type: KtNonErrorClassType): List<String>? {
    if (type.nullability != KtTypeNullability.NON_NULLABLE) return null
    val classSymbol = type.expandedClassSymbol

    return if (classSymbol is KtNamedClassOrObjectSymbol && classSymbol.isData) {
        val constructorSymbol = classSymbol.getDeclaredMemberScope()
            .getConstructors()
            .find { it.isPrimary }
            ?: return null

        constructorSymbol.valueParameters.map { it.name.asString() }
    } else null
}

context(KtAnalysisSession)
private fun getClassType(declaration: KtDestructuringDeclaration): KtNonErrorClassType? {
    val initializer = declaration.initializer
    val type = if (initializer != null) {
        initializer.getKtType()
    } else {
        val parentAsParameter = declaration.parent as? KtParameter
        parentAsParameter?.getParameterSymbol()?.returnType
    }
    return toNonErrorClassType(type)
}

private fun toNonErrorClassType(type: KtType?): KtNonErrorClassType? {
    return when (type) {
        is KtNonErrorClassType -> type
        is KtFlexibleType -> type.lowerBound as? KtNonErrorClassType
        else -> null
    }
}