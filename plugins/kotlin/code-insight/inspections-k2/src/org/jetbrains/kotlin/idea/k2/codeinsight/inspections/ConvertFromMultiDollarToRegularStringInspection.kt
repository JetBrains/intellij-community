// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.convertToStringWithoutPrefix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.simplifyDollarEntries
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Intention-like inspection with [com.intellij.codeInspection.ProblemHighlightType.INFORMATION] level to allow applying the conversion for the whole project.
 *
 * Removes the interpolation prefix from string literals and updates their content to preserve the meaning.
 */
internal class ConvertFromMultiDollarToRegularStringInspection :
    AbstractApplicabilityBasedInspection<KtStringTemplateExpression>(KtStringTemplateExpression::class.java) {

    override fun inspectionText(element: KtStringTemplateExpression): String =
        KotlinBundle.message("inspection.remove.interpolation.prefix.problem.description")

    override val defaultFixText: String
        get() = KotlinBundle.message("inspection.remove.interpolation.prefix.quick.fix.text")

    override fun isApplicable(element: KtStringTemplateExpression): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.MultiDollarInterpolation)) return false
        if (element.interpolationPrefix == null) return false
        return true
    }

    override fun applyTo(element: KtStringTemplateExpression, project: Project, editor: Editor?) {
        // createQuickFix is overridden
    }

    override fun createQuickFix(element: KtStringTemplateExpression): KotlinModCommandQuickFix<KtStringTemplateExpression> {
        return object : KotlinModCommandQuickFix<KtStringTemplateExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String = defaultFixText

            override fun applyFix(
                project: Project,
                element: KtStringTemplateExpression,
                updater: ModPsiUpdater
            ) {
                val stringWithoutPrefix = convertToStringWithoutPrefix(element)
                simplifyDollarEntries(stringWithoutPrefix)
            }
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }
}