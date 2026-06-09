// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.NameBasedDestructuringForm
import org.jetbrains.kotlin.idea.codeinsight.utils.buildNameBasedDestructuringText
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.isPositionalDestructuringType
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor

internal class ConvertNameBasedDestructuringShortFormToFullInspection : KotlinApplicableInspectionBase.Simple<KtDestructuringDeclaration, String>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = destructuringDeclarationVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtDestructuringDeclaration): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.NameBasedDestructuring)) return false
        // Allow destructuring declarations with initializers OR lambda parameters
        if (element.initializer == null && element.parent !is KtParameter) return false
        if (element.entries.isEmpty()) return false
        if (element.isFullForm) return false
        return true
    }

    override fun getProblemDescription(element: KtDestructuringDeclaration, context: String): String {
        return KotlinBundle.message("convert.to.full.name.based.form.destructing")
    }

    override fun createQuickFix(
        element: KtDestructuringDeclaration,
        context: String
    ): KotlinModCommandQuickFix<KtDestructuringDeclaration> {
        return ConvertNameBasedDestructuringShortFormToFullFix(context)
    }

    override fun KaSession.prepareContext(element: KtDestructuringDeclaration): String? {
        val names = extractPrimaryParameters(element)?.map { it.name.asString() } ?: return null
        // Exclude stdlib types - they should use brackets [x, y] instead
        if (element.isPositionalDestructuringType()) return null
        return element.buildNameBasedDestructuringText(
            NameBasedDestructuringForm(names, positionBased = false, useFullForm = true)
        )
    }
}

internal class ConvertNameBasedDestructuringShortFormToFullFix(private val newDestructuringText: String) : KotlinModCommandQuickFix<KtDestructuringDeclaration>() {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.full.name.based.form.destructing")

    override fun applyFix(
        project: Project,
        element: KtDestructuringDeclaration,
        updater: ModPsiUpdater
    ) {
        // Create the new destructuring declaration and replace the entire declaration
        val psiFactory = KtPsiFactory(project)
        val newDestructuringDeclaration = psiFactory.createDestructuringDeclaration(newDestructuringText)
        element.replace(newDestructuringDeclaration)
    }
}
