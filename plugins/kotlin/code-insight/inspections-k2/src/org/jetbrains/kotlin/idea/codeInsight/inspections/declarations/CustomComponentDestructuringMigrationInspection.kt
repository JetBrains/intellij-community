// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.declarations

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.convertDestructuringToPositionalForm
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.getDestructuredClassType
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val MAP_ENTRY_NAMES = listOf("key", "value")

/**
 * Inspection that warns about destructuring declarations that use custom componentN operators
 * when the NameBasedDestructuring language feature is enabled.
 *
 * This is part of the migration plan for KEEP-0438.
 * Position-based destructuring should use square brackets `[x, y]` instead of parentheses `(x, y)`.
 *
 * The inspection warns when:
 * - Destructuring a non-data-class (all components are custom)
 * - Destructuring a data class with more entries than primary constructor parameters
 *   (extra components come from custom componentN operators)
 *
 * Examples that warn:
 * - `val (x, y) = listOf(1, 2)`           — `x`/`y` are not List properties
 * - `for ((key, v) in mapOf(...))`        — `v` is not a Map.Entry property
 * - `val (a, b, c) = dataClassWithTwoFields` — `component3` must be a custom extension
 *
 * Examples that do NOT warn:
 * - `for ((key, value) in mapOf(...))`    — Map.Entry destructuring
 */
internal class CustomComponentDestructuringMigrationInspection : AbstractKotlinInspection() {

    override fun isAvailableForFile(file: PsiFile): Boolean {
        val settings = file.languageVersionSettings
        return settings.supportsFeature(LanguageFeature.NameBasedDestructuring) &&
                !settings.supportsFeature(LanguageFeature.DeprecateNameMismatchInShortDestructuringWithParentheses)
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitDestructuringDeclaration(declaration: KtDestructuringDeclaration) {
            processDestructuringDeclaration(holder, declaration)
        }
    }

    private fun processDestructuringDeclaration(holder: ProblemsHolder, declaration: KtDestructuringDeclaration) {
        if (declaration.hasSquareBrackets()) return
        if (declaration.entries.isEmpty()) return

        val requiresPositionBasedDestructuringMigration = analyze(declaration) {
            val primaryParameters = extractPrimaryParameters(declaration)
            // Warn when a custom componentN extension is needed (not a data class, or
            // more entries than the data-class parameters), unless the destructuring
            // is the `Map.Entry` form `(key, value)`.
            (primaryParameters == null || declaration.entries.size > primaryParameters.size) && !isMapEntryDestructuring(declaration)
        }
        if (!requiresPositionBasedDestructuringMigration) return

        val highlightRange = ApplicabilityRanges.destructuringDeclarationParens(declaration).singleOrNull() ?: return

        holder.registerProblem(
            declaration,
            highlightRange,
            KotlinBundle.message("inspection.positional.destructuring.migration"),
            ConvertCustomComponentDestructuringToSquareBracketFix()
        )
    }

    context(session: KaSession)
    private fun isMapEntryDestructuring(declaration: KtDestructuringDeclaration): Boolean {
        val classType = declaration.getDestructuredClassType() ?: return false
        if (!classType.isSubtypeOf(StandardClassIds.MapEntry)) return false

        val entries = declaration.entries
        if (entries.size > MAP_ENTRY_NAMES.size) return false
        return entries.zip(MAP_ENTRY_NAMES).all { (entry, expected) -> entry.name == expected }
    }
}

private class ConvertCustomComponentDestructuringToSquareBracketFix : KotlinModCommandQuickFix<KtDestructuringDeclaration>() {

    override fun getFamilyName(): String = KotlinBundle.message("inspection.positional.destructuring.migration.fix")

    override fun applyFix(
        project: Project,
        element: KtDestructuringDeclaration,
        updater: ModPsiUpdater
    ) {
        convertDestructuringToPositionalForm(element)
    }
}