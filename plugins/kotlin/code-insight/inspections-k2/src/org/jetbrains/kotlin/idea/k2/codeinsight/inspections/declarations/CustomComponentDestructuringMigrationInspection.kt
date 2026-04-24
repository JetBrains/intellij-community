// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.convertDestructuringToPositionalForm
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

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
 * Examples:
 * - `val (x, y) = listOf(1, 2)` - warns (uses custom componentN)
 * - `for ((key, value) in mapOf(1 to "one"))` - warns (uses custom componentN)
 * - `val (a, b, c) = dataClassWithTwoFields` - warns (component3 is custom)
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

        val usesCustomComponents = analyze(declaration) {
            val primaryParameters = extractPrimaryParameters(declaration)
            // Warn if not a data class, or if using more components than the data class provides
            primaryParameters == null || declaration.entries.size > primaryParameters.size
        }

        if (!usesCustomComponents) return

        val highlightRange = ApplicabilityRanges.destructuringDeclarationParens(declaration).singleOrNull() ?: return

        holder.registerProblem(
            declaration,
            highlightRange,
            KotlinBundle.message("inspection.positional.destructuring.migration"),
            ConvertCustomComponentDestructuringToSquareBracketFix()
        )
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