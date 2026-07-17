// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.simpleNameExpressionVisitor

internal class EscapingCapturedVariableInspection : KotlinApplicableInspectionBase<KtSimpleNameExpression, String>() {

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtSimpleNameExpression): String? =
        element.directDiagnostics(KaDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
            .firstNotNullOfOrNull { it as? KaFirDiagnostic.EscapingCapturedVariable }
            ?.variable?.name?.asString()

    override fun InspectionManager.createProblemDescriptor(
        element: KtSimpleNameExpression,
        context: String,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */ rangeInElement,
        /* descriptionTemplate = */ KotlinBundle.message("escaping.captured.variable", context),
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
        /* ...fixes = */ *LocalQuickFix.EMPTY_ARRAY,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = simpleNameExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}
