// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullInspection.Context
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.NameBasedDestructuringForm
import org.jetbrains.kotlin.idea.codeinsight.utils.applyNameBasedDestructuringForm
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.isPositionalDestructuringType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor

internal class ConvertNameBasedDestructuringShortFormToFullInspection :
    KotlinApplicableInspectionBase.Simple<KtDestructuringDeclaration, Context>() {

    data class Context(
        val propertyNames: List<Name>,
    )

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

    override fun getProblemDescription(element: KtDestructuringDeclaration, context: Context): String {
        return KotlinBundle.message("convert.to.full.name.based.form.destructing")
    }

    override fun createQuickFix(
        element: KtDestructuringDeclaration,
        context: Context
    ): KotlinModCommandQuickFix<KtDestructuringDeclaration> {
        return ConvertNameBasedDestructuringShortFormToFullFix(context)
    }

    override fun KaSession.prepareContext(element: KtDestructuringDeclaration): Context? {
        if (element.isPositionalDestructuringType()) return null
        if (element.entries.all { it.name == "_" }) return null
        val constructorParameters = extractPrimaryParameters(element) ?: return null
        return Context(constructorParameters.take(element.entries.size).map { it.name })
    }
}

internal class ConvertNameBasedDestructuringShortFormToFullFix(private val context: Context) :
    KotlinModCommandQuickFix<KtDestructuringDeclaration>() {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.full.name.based.form.destructing")

    override fun applyFix(
        project: Project,
        element: KtDestructuringDeclaration,
        updater: ModPsiUpdater,
    ) {
        element.applyNameBasedDestructuringForm(NameBasedDestructuringForm(context.propertyNames, positionBased = false, useFullForm = true))
    }
}
