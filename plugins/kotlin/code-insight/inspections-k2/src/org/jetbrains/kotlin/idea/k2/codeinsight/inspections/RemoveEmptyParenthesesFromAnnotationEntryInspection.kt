// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.valueArgumentListVisitor

internal class RemoveEmptyParenthesesFromAnnotationEntryInspection : KotlinApplicableInspectionBase.Simple<KtValueArgumentList, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = valueArgumentListVisitor { list ->
        visitTargetElement(list, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtValueArgumentList,
        context: Unit,
    ): String = KotlinBundle.message("parentheses.should.be.removed")

    override fun getApplicableRanges(element: KtValueArgumentList): List<TextRange> =
        ApplicabilityRange.self(element)

    override fun isApplicableByPsi(element: KtValueArgumentList): Boolean {
        if (element.arguments.isNotEmpty()) return false
        val annotationEntry = element.parent as? KtAnnotationEntry ?: return false
        return annotationEntry.typeArguments.isEmpty()
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtValueArgumentList): Unit? {
        val annotationEntry = element.parent as? KtAnnotationEntry ?: return null
        return annotationEntry.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).none {
            it is KaFirDiagnostic.NoValueForParameter
        }.asUnit
    }

    override fun createQuickFix(
        element: KtValueArgumentList,
        context: Unit,
    ): KotlinModCommandQuickFix<KtValueArgumentList> = object : KotlinModCommandQuickFix<KtValueArgumentList>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.empty.parentheses.from.annotation.entry.fix.text")

        override fun applyFix(
            project: Project,
            element: KtValueArgumentList,
            updater: ModPsiUpdater,
        ): Unit = element.delete()
    }
}
