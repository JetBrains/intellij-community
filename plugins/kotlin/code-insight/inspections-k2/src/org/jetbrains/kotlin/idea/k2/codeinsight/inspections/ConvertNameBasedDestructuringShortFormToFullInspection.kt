// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.isPositionalDestructuringType
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class ConvertNameBasedDestructuringShortFormToFullInspection : KotlinApplicableInspectionBase.Simple<KtDestructuringDeclaration, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitDestructuringDeclaration(declaration: KtDestructuringDeclaration) {
            visitTargetElement(declaration, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtDestructuringDeclaration): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.NameBasedDestructuring)) return false
        // Allow destructuring declarations with initializers OR lambda parameters
        if (element.initializer == null && element.parent !is KtParameter) return false
        if (element.entries.isEmpty()) return false
        if (element.isFullForm) return false
        return true
    }

    override fun getProblemDescription(element: KtDestructuringDeclaration, context: Unit): String {
        return KotlinBundle.message("convert.to.full.name.based.form.destructing")
    }

    override fun createQuickFix(
        element: KtDestructuringDeclaration,
        context: Unit
    ): KotlinModCommandQuickFix<KtDestructuringDeclaration> {
        return ConvertNameBasedDestructuringShortFormToFullFix()
    }

    override fun KaSession.prepareContext(element: KtDestructuringDeclaration): Unit? {
        // Just verify that we can extract primary parameters - the actual work will be done in the QuickFix
        if (extractPrimaryParameters(element) == null) return null
        // Exclude stdlib types - they should use brackets [x, y] instead
        if (element.isPositionalDestructuringType()) return null
        return Unit
    }
}

internal class ConvertNameBasedDestructuringShortFormToFullFix : KotlinModCommandQuickFix<KtDestructuringDeclaration>() {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.full.name.based.form.destructing")

    override fun applyFix(
        project: Project,
        element: KtDestructuringDeclaration,
        updater: ModPsiUpdater
    ) {
        // For regular destructuring declarations with initializers
        val initializerText = element.initializer?.text

        // Determine if we're using 'val' or 'var'
        val keyword = if (element.isVar) "var" else "val"

        // Build the new destructuring declaration with explicit names
        val newEntries = analyze(element) {
            val constructorParameters = extractPrimaryParameters(element) ?: return@analyze ""

            element.entries.zip(constructorParameters) { entry, param ->
                val entryName = entry.nameAsName
                val originalPropertyName = param.name

                if (entryName == null || entryName != originalPropertyName) {
                    "$keyword ${entry.text} = $originalPropertyName"
                } else {
                    "$keyword ${entry.name}"
                }
            }.joinToString(", ")
        }

        val newDestructuringText = if (initializerText != null) {
            "($newEntries) = $initializerText"
        } else {
            // For lambda parameters
            "($newEntries)"
        }

        // Create the new destructuring declaration and replace the entire declaration
        val psiFactory = KtPsiFactory(project)
        val newDestructuringDeclaration = psiFactory.createDestructuringDeclaration(newDestructuringText)
        element.replace(newDestructuringDeclaration)
    }
}