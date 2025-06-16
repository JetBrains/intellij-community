// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinPsiDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.reflect.KClass

internal class RedundantInterpolationPrefixInspection :
    KotlinPsiDiagnosticBasedInspectionBase<KtElement, KaFirDiagnostic.RedundantInterpolationPrefix, Unit>() {
    override val diagnosticType: KClass<KaFirDiagnostic.RedundantInterpolationPrefix>
        get() = KaFirDiagnostic.RedundantInterpolationPrefix::class

    override val diagnosticFilter: KaDiagnosticCheckerFilter = KaDiagnosticCheckerFilter.ONLY_EXPERIMENTAL_CHECKERS

    override fun isApplicableByPsi(element: KtElement): Boolean =
        element is KtStringTemplateExpression && element.interpolationPrefix != null

    override fun getApplicableRanges(element: KtElement): List<TextRange> {
        return ApplicabilityRange.single(element) { templateExpression ->
            templateExpression.safeAs<KtStringTemplateExpression>()?.interpolationPrefix
        }
    }

    override fun KaSession.prepareContextByDiagnostic(
        element: KtElement,
        diagnostic: KaFirDiagnostic.RedundantInterpolationPrefix
    ): Unit = Unit

    override fun getProblemDescription(
        element: KtElement,
        context: Unit
    ): @InspectionMessage String {
        return KotlinBundle.message("inspection.redundant.interpolation.prefix.problem.description")
    }

    override fun createQuickFix(
        element: KtElement,
        context: Unit
    ): KotlinModCommandQuickFix<KtElement> = RemoveRedundantInterpolationQuickFix

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> {
        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
}

private object RemoveRedundantInterpolationQuickFix : KotlinModCommandQuickFix<KtElement>() {
    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("inspection.redundant.interpolation.prefix.quick.fix.text")
    }

    override fun applyFix(
        project: Project,
        element: KtElement,
        updater: ModPsiUpdater
    ) {
        if (element !is KtStringTemplateExpression) return
        val interpolationPrefix = element.interpolationPrefix ?: return
        interpolationPrefix.delete()
    }
}
