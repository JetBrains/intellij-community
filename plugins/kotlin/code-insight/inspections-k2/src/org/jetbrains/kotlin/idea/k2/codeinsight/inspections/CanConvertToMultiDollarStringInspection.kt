// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.*
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

class CanConvertToMultiDollarStringInspection :
    KotlinApplicableInspectionBase.Simple<KtStringTemplateExpression, MultiDollarConversionInfo>() {

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.MultiDollarInterpolation)) return false
        return element.interpolationPrefix == null
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> {
        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(entry: KtStringTemplateExpression) {
                visitTargetElement(entry, holder, isOnTheFly)
            }
        }
    }

    context(KaSession)
    override fun prepareContext(element: KtStringTemplateExpression): MultiDollarConversionInfo? {
        if (!element.entries.any { it.isEscapedDollar() }) return null
        return prepareMultiDollarConversionInfo(element, useFallbackPrefix = false)
    }

    override fun getProblemDescription(
        element: KtStringTemplateExpression,
        context: MultiDollarConversionInfo,
    ): @InspectionMessage String {
        return KotlinBundle.message("inspection.can.convert.to.multi.dollar.string.problem.description")
    }

    override fun createQuickFix(
        element: KtStringTemplateExpression,
        context: MultiDollarConversionInfo,
    ): KotlinModCommandQuickFix<KtStringTemplateExpression> {
        return object : KotlinModCommandQuickFix<KtStringTemplateExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String {
                return KotlinBundle.message("add.interpolation.prefix")
            }

            override fun applyFix(
                project: Project,
                element: KtStringTemplateExpression,
                updater: ModPsiUpdater
            ) {
                val multiDollarVersion = convertToMultiDollarString(element, context)
                simplifyDollarEntries(multiDollarVersion)
            }
        }
    }
}
