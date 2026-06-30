// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DestructuringUtils")

package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.lowerBoundIfFlexible
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
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
    val type = declaration.getDestructuredClassType() ?: return null
    return extractDataClassParameters(type)?.takeIf { parameters ->
        declaration.entries.size <= parameters.size
    }
}

fun KaSession.extractDataClassParameters(type: KaClassType): List<KaValueParameterSymbol>? {
    if (type.isMarkedNullable) return null
    val classSymbol = type.expandedSymbol

    return if (classSymbol is KaNamedClassSymbol && classSymbol.isData) {
        val constructorSymbol = classSymbol.declaredMemberScope
            .constructors
            .find { it.isPrimary }
            ?: return null

        constructorSymbol.valueParameters
    } else null
}

/**
 * Returns the class type of the value being destructured: either the initializer expression's type
 * or the destructured parameter's type (lambda case)
 */
@OptIn(KaContextParameterApi::class)
@ApiStatus.Internal
context(_: KaSession)
fun KtDestructuringDeclaration.getDestructuredClassType(): KaClassType? {
    val type = initializer?.expressionType ?: (parent as? KtParameter)?.symbol?.returnType ?: return null
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
@ApiStatus.Internal
context(session: KaSession)
fun KtDestructuringDeclaration.isPositionalDestructuringType(): Boolean {
    val classType = this.getDestructuredClassType() ?: return false
    return session.isPositionalDestructuringType(classType)
}

@ApiStatus.Internal
fun KaSession.isPositionalDestructuringType(classType: KaClassType): Boolean {
    val classId = classType.expandedSymbol?.classId ?: return false
    return classId in POSITIONAL_DESTRUCTURING_CLASSES
}

@ApiStatus.Internal
fun KaSession.buildFullNameBasedDestructuringFormText(declaration: KtDestructuringDeclaration): String? {
    if (declaration.isPositionalDestructuringType()) return null
    val names = extractPrimaryParameters(declaration)?.map { it.name.asString() } ?: return null
    // Exclude stdlib types - they should use brackets [x, y] instead
    return declaration.buildNameBasedDestructuringText(
        NameBasedDestructuringForm(names, positionBased = false, useFullForm = true)
    )
}

@ApiStatus.Internal
fun KtDestructuringDeclaration.buildNameBasedDestructuringText(
    nameBasedDestructuringForm: NameBasedDestructuringForm,
    useExplicitMappings: Boolean = false,
    entityNames: List<String>? = null
): String? {
    val destructuringNames = nameBasedDestructuringForm.names
    val names = entityNames ?: entries.map { it.text.substringBefore('=').trim() }
    if (names.size > destructuringNames.size) return null

    val positionBased = nameBasedDestructuringForm.positionBased
    val useShortForm = !nameBasedDestructuringForm.useFullForm
    val originalKeyword = if (isVar) "var" else "val"
    val keyword = "".takeIf { positionBased || useShortForm } ?: originalKeyword
    val newEntries = names
        .zip(destructuringNames) { entry, name ->
            if (entry == "_") return@zip null
            buildString {
                append(keyword)
                if (keyword.isNotEmpty()) {
                    append(" ")
                }
                append(entry)
                if (!positionBased && (useExplicitMappings || entry != name)) {
                    append(" = ")
                    append(name)
                }
            }
        }
        .filterNotNull()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
        ?: return null

    val declarationText =
        buildString {
            if (positionBased || useShortForm) {
                append(originalKeyword)
                append(" ")
            }
            append(nameBasedDestructuringForm.leftParenthesis)
            append(newEntries)
            append(nameBasedDestructuringForm.rightParenthesis)
        }
    return initializer?.let { "$declarationText = ${it.text}" } ?: declarationText
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

@ApiStatus.Internal
data class NameBasedDestructuringForm(
    val names: List<String>,
    val positionBased: Boolean,
    val useFullForm: Boolean,
) {
    val leftParenthesis: String
        get() = "[".takeIf { positionBased } ?: "("
    val rightParenthesis: String
        get() = "]".takeIf { positionBased } ?: ")"
}

