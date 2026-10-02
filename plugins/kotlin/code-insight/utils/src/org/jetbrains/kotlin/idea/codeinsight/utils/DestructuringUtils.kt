// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DestructuringUtils")

package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.scopes.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.idea.base.psi.EditCommaSeparatedListHelper
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.util.isSingleUnderscore

private val MAP_ENTRY_NAMES: List<Name> = listOf(Name.identifier("key"), Name.identifier("value"))
/**
 * Extracts primary constructor parameters for a data class destructuring declaration.
 *
 * Returns non-null only if all destructuring entries can be matched to constructor parameters
 * (i.e., the number of entries does not exceed the number of parameters).
 */
context(session: KaSession)
fun extractPrimaryParameters(
    declaration: KtDestructuringDeclaration,
): List<KaValueParameterSymbol>? {
    val type = declaration.getDestructuredClassType() ?: return null
    return extractDataClassParameters(type)?.takeIf { parameters ->
        declaration.entries.size <= parameters.size
    }
}

context(session: KaSession)
fun extractDataClassParameters(type: KaClassType): List<KaValueParameterSymbol>? {
    if (type.isMarkedNullable) return null
    val classSymbol = type.expandedSymbol as? KaNamedClassSymbol ?: return null

    return if (classSymbol.isData || classSymbol.isFullValueClass()) {
        val constructorSymbol = classSymbol.declaredMemberScope
            .constructors
            .find { it.isPrimary }
            ?: return null

        constructorSymbol.valueParameters
    } else null
}

/**
 * Returns true for a full value class destructuring declaration.
 *
 * Old JVM inline value classes stay out of this check.
 */
@ApiStatus.Internal
context(_: KaSession)
fun KtDestructuringDeclaration.isFullValueClassDestructuring(): Boolean {
    val type = getDestructuredClassType() ?: return false
    if (type.isMarkedNullable) return false
    val classSymbol = type.expandedSymbol as? KaNamedClassSymbol ?: return false
    return classSymbol.isFullValueClass()
}

@ApiStatus.Internal
fun KaSession.extractDestructuringComponentNames(declaration: KtDestructuringDeclaration): List<Name>? {
    val classType = declaration.getDestructuredClassType() ?: return null
    val entryCount = declaration.entries.size

    val allNames = when {
        classType.isSubtypeOf(StandardClassIds.MapEntry) -> MAP_ENTRY_NAMES
        else -> extractDataClassParameters(classType)?.map { it.name } ?: return null
    }

    if (entryCount > allNames.size) return null
    return allNames.take(entryCount)
}

/**
 * Returns the class type of the value being destructured: either the initializer expression's type
 * or the destructured parameter's type (lambda case)
 */
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
    return isPositionalDestructuringType(classType)
}

@ApiStatus.Internal
context(session: KaSession)
fun isPositionalDestructuringType(classType: KaClassType): Boolean {
    val classId = classType.expandedSymbol?.classId ?: return false
    return classId in POSITIONAL_DESTRUCTURING_CLASSES
}

private fun getNewEntriesText(entryMappings: List<EntryMapping>, perEntryKeyword: String, positionBased: Boolean): String {
    return entryMappings.joinToString(", ") { mapping ->
        buildString {
            if (perEntryKeyword.isNotEmpty()) {
                append(perEntryKeyword)
                append(" ")
            }
            append(mapping.usedName)

            if (!positionBased && mapping.usedName != mapping.targetName.asString()) {
                append(" = ")
                append(mapping.targetName.asString())
            }
        }
    }
}

private fun KtDestructuringDeclaration.buildEntryMappings(usedNames: List<String>, targetNames: List<Name>): List<EntryMapping>? {
    if (entries.size > targetNames.size || entries.size > usedNames.size) return null

    return entries.mapIndexed { index, entry ->
        EntryMapping(entry, usedNames[index], targetNames[index])
    }
}

@ApiStatus.Internal
fun KtDestructuringDeclaration.applyNameBasedDestructuringForm(
    nameBasedDestructuringForm: NameBasedDestructuringForm,
    entityNames: List<String>? = null
): KtDestructuringDeclaration? {
    val targetNames = nameBasedDestructuringForm.names
    val usedNames = entityNames ?: entries.map { it.name ?: return null }

    val entryMappings = buildEntryMappings(usedNames, targetNames) ?: return null

    val positionBased = nameBasedDestructuringForm.positionBased
    val useShortForm = !nameBasedDestructuringForm.useFullForm

    val outerKeyword = if (isVar) "var" else "val"
    val perEntryKeyword = if (!positionBased && !useShortForm) outerKeyword else ""

    val (keptMappings, underscoreMappingsToDrop) = entryMappings.partition { !it.psiEntry.isSingleUnderscore }

    if (keptMappings.isEmpty()) return null

    val newEntriesText = getNewEntriesText(keptMappings, perEntryKeyword, positionBased)

    val templateKeyword = if (perEntryKeyword.isEmpty()) "$outerKeyword " else ""
    val template = KtPsiFactory(project).createDestructuringDeclaration("$templateKeyword($newEntriesText) = TODO()")

    if (template.entries.size != keptMappings.size) return null
    keptMappings.zip(template.entries).forEach { (mapping, newEntry) ->
        mapping.psiEntry.replace(newEntry)
    }

    underscoreMappingsToDrop.forEach { mapping -> EditCommaSeparatedListHelper.removeItem(mapping.psiEntry) }

    val shouldRemoveOuterKeyword = !positionBased && !useShortForm
    if (shouldRemoveOuterKeyword) { valOrVarKeyword?.delete() }

    if (positionBased) { convertDestructuringToPositionalForm(this) }
    return this
}

@ApiStatus.Internal
fun dropDestructuringEntry(entry: KtDestructuringDeclarationEntry) {
    val declaration = entry.parent as? KtDestructuringDeclaration ?: return
    if (declaration.entries.size <= 1) {
        declaration.delete()
    } else {
        EditCommaSeparatedListHelper.removeItem(entry)
    }
}

@ApiStatus.Internal
fun renameNameBasedDestructuringEntryToUnderscore(entry: KtDestructuringDeclarationEntry) {
    val originalName = entry.nameIdentifier?.text ?: return
    if (entry.initializer == null) {
        val psiFactory = KtPsiFactory(entry.project)

        val anchor = entry.typeReference ?: entry.nameIdentifier ?: return
        val initializerSeparator = entry.addAfter(psiFactory.createEQ(), anchor)

        entry.addAfter(psiFactory.createExpression(originalName), initializerSeparator)
    }
    renameToUnderscore(entry)
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
