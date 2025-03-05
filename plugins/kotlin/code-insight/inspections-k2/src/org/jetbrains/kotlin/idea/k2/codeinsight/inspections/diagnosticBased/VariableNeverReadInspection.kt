// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.*
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.namedDeclarationVisitor
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

internal class VariableNeverReadInspection : KotlinApplicableInspectionBase<KtNamedDeclaration, Unit>() {

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtNamedDeclaration): Unit? {
        return element
            .diagnostics(KaDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
            .firstIsInstanceOrNull<KaFirDiagnostic.VariableNeverRead>()
            ?.let {}
    }

    override fun getApplicableRanges(element: KtNamedDeclaration): List<TextRange> = ApplicabilityRanges.declarationName(element)

    override fun InspectionManager.createProblemDescriptor(
        element: KtNamedDeclaration,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */ rangeInElement,
        /* descriptionTemplate = */ KotlinBundle.message("variable.is.never.read", element.name.toString()),
        /* highlightType = */ ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        /* onTheFly = */ false,
        /* ...fixes = */ *LocalQuickFix.EMPTY_ARRAY // TODO KTIJ-33011
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = namedDeclarationVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}
