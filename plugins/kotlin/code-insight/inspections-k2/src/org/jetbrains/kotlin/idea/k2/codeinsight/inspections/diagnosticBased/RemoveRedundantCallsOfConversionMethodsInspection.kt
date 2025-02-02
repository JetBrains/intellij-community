// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinPsiDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor
import kotlin.reflect.KClass

internal class RemoveRedundantCallsOfConversionMethodsInspection :
    KotlinPsiDiagnosticBasedInspectionBase<KtQualifiedExpression, KaFirDiagnostic.RedundantCallOfConversionMethod, Unit>() {

    override val diagnosticType: KClass<KaFirDiagnostic.RedundantCallOfConversionMethod>
        get() = KaFirDiagnostic.RedundantCallOfConversionMethod::class

    override fun KaSession.prepareContextByDiagnostic(
        element: KtQualifiedExpression,
        diagnostic: KaFirDiagnostic.RedundantCallOfConversionMethod
    ): Unit = Unit

    override fun getApplicableRanges(element: KtQualifiedExpression): List<TextRange> {
        return ApplicabilityRange.single(element) {
            it.selectorExpression
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = qualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtQualifiedExpression,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("redundant.call.of.the.conversion.method")

    override fun createQuickFixes(
        element: KtQualifiedExpression,
        context: Unit
    ): Array<KotlinModCommandQuickFix<KtQualifiedExpression>> = arrayOf(object : KotlinModCommandQuickFix<KtQualifiedExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.redundant.calls.of.the.conversion.method")

        override fun applyFix(
            project: Project,
            element: KtQualifiedExpression,
            updater: ModPsiUpdater
        ) {
            element.replace(element.receiverExpression)
        }
    })
}