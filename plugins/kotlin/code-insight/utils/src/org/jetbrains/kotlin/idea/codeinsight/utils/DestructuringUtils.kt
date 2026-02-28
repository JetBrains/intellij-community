// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DestructuringUtils")

package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Extracts primary constructor parameters for a data class destructuring declaration.
 *
 * Returns non-null only if all destructuring entries can be matched to constructor parameters
 * (i.e., the number of entries does not exceed the number of parameters).
 */
fun KaSession.extractPrimaryParameters(
    declaration: KtDestructuringDeclaration,
): List<KaValueParameterSymbol>? {
    val type = getClassType(declaration) ?: return null
    return extractDataClassParameters(type)?.takeIf { parameters ->
        declaration.entries.size <= parameters.size
    }
}

fun KaSession.extractDataClassParameters(type: KaClassType): List<KaValueParameterSymbol>? {
    if (type.nullability != KaTypeNullability.NON_NULLABLE) return null
    val classSymbol = type.expandedSymbol

    return if (classSymbol is KaNamedClassSymbol && classSymbol.isData) {
        val constructorSymbol = classSymbol.declaredMemberScope
            .constructors
            .find { it.isPrimary }
            ?: return null

        constructorSymbol.valueParameters
    } else null
}

private fun KaSession.getClassType(declaration: KtDestructuringDeclaration): KaClassType? {
    val type = declaration.initializer?.expressionType
        ?: (declaration.parent as? KtParameter)?.symbol?.returnType
        ?: return null
    return type.lowerBoundIfFlexible() as? KaClassType
}

/**
 * Set of known data classes from STDLIB for which the positional destructuring syntax is preferred.
 */
private val POSITIONAL_DESTRUCTURING_CLASSES: Set<ClassId> = setOf(
    StandardKotlinNames.Pair,
    StandardKotlinNames.Triple,
    StandardKotlinNames.Collections.IndexedValue,
)

/**
 * Checks if the destructured type is intended for positional destructuring (Pair, Triple, IndexedValue).
 * These types should use bracket syntax [x, y] instead of name-based destructuring.
 */
@OptIn(KaContextParameterApi::class)
context(session: KaSession)
fun KtDestructuringDeclaration.isPositionalDestructuringType(): Boolean {
    val classType = session.getClassType(this) ?: return false
    val classId = classType.expandedSymbol?.classId
    return classId in POSITIONAL_DESTRUCTURING_CLASSES
}

/**
 * Replaces parentheses with square brackets in a destructuring declaration (positional form).
 */
@ApiStatus.Internal
fun convertDestructuringToPositionalForm(declaration: KtDestructuringDeclaration) {
    val lPar = declaration.lPar ?: return
    val rPar = declaration.rPar ?: return

    val bracketDecl = KtPsiFactory(declaration.project).createDestructuringDeclaration("val [a] = null")
    val lBracket = bracketDecl.lPar ?: return
    val rBracket = bracketDecl.rPar ?: return

    lPar.replace(lBracket)
    rPar.replace(rBracket)
}
