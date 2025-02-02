// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinKtDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.namedDeclarationVisitor
import kotlin.reflect.KClass

internal class VariableNeverReadInspection : KotlinKtDiagnosticBasedInspectionBase<KtNamedDeclaration, KaFirDiagnostic.VariableNeverRead, Unit>() {
    override val diagnosticType: KClass<KaFirDiagnostic.VariableNeverRead>
        get() = KaFirDiagnostic.VariableNeverRead::class

    override fun KaSession.prepareContextByDiagnostic(
        element: KtNamedDeclaration,
        diagnostic: KaFirDiagnostic.VariableNeverRead,
    ): Unit = Unit

    override fun getProblemDescription(
        element: KtNamedDeclaration,
        context: Unit,
    ): @InspectionMessage String = KotlinBundle.message("variable.is.never.read", element.name.toString())

    override fun getApplicableRanges(element: KtNamedDeclaration): List<TextRange> = ApplicabilityRanges.declarationName(element)

    override fun getProblemHighlightType(element: KtNamedDeclaration, context: Unit): ProblemHighlightType =
        ProblemHighlightType.LIKE_UNUSED_SYMBOL

    override fun createQuickFixes(
        element: KtNamedDeclaration,
        context: Unit,
    ): Array<KotlinModCommandQuickFix<KtNamedDeclaration>> = emptyArray() // KTIJ-29530

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = namedDeclarationVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}
