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
import org.jetbrains.kotlin.idea.base.psi.EditCommaSeparatedListHelper
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
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
fun KtDestructuringDeclaration.applyNameBasedDestructuringForm(
    nameBasedDestructuringForm: NameBasedDestructuringForm,
    entityNames: List<String>? = null
): KtDestructuringDeclaration? {
    val destructuringNames = nameBasedDestructuringForm.names
    val usedNames = entityNames ?: entries.map { it.name ?: return null }
    if (entries.size > destructuringNames.size || entries.size > usedNames.size) return null

    val positionBased = nameBasedDestructuringForm.positionBased
    val useShortForm = !nameBasedDestructuringForm.useFullForm
    val originalKeyword = if (isVar) "var" else "val"
    val keyword = if (positionBased || useShortForm) "" else originalKeyword

    val entryMappings = entries.mapIndexed { i, entry ->
        EntryMapping(entry, usedNames[i], destructuringNames[i])
    }
    // entries with "_" name will be removed further
    val (kept, dropped) = entryMappings.partition { it.usedName != "_" }
    if (kept.isEmpty()) return null

    val newEntriesText = kept.joinToString(", ") { mapping ->
        buildString {
            if (keyword.isNotEmpty()) {
                append(keyword)
                append(" ")
            }
            append(mapping.usedName)
            if (!positionBased && (mapping.usedName != mapping.targetName.asString())) {
                append(" = ")
                append(mapping.targetName)
            }
        }
    }

    val templateKeyword = if (keyword.isEmpty()) "$originalKeyword " else ""
    val template = KtPsiFactory(project).createDestructuringDeclaration("$templateKeyword($newEntriesText) = TODO()")

    if (template.entries.size != kept.size) return null
    kept.zip(template.entries).forEach { (mapping, newEntry) ->
        mapping.psiEntry.replace(newEntry)
    }
    dropped.forEach {
        EditCommaSeparatedListHelper.removeItem(it.psiEntry)
    }

    if (!positionBased && !useShortForm) {
        valOrVarKeyword?.delete()
    }

    if (positionBased) {
        convertDestructuringToPositionalForm(this)
    }
    return this
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
    val names: List<Name>,
    val positionBased: Boolean,
    val useFullForm: Boolean,
)

@ApiStatus.Internal
private data class EntryMapping(
    val psiEntry: KtDestructuringDeclarationEntry,
    val usedName: String,
    val targetName: Name
)
