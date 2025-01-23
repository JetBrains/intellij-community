// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinPsiDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.expressionVisitor
import kotlin.reflect.KClass

internal class AssignedValueIsNeverReadInspection : KotlinPsiDiagnosticBasedInspectionBase<KtExpression, KaFirDiagnostic.AssignedValueIsNeverRead, Unit>() {
    override val diagnosticType: KClass<KaFirDiagnostic.AssignedValueIsNeverRead>
        get() = KaFirDiagnostic.AssignedValueIsNeverRead::class

    context(KaSession@KaSession)
    override fun prepareContextByDiagnostic(
        element: KtExpression,
        diagnostic: KaFirDiagnostic.AssignedValueIsNeverRead,
    ): Unit = Unit

    override fun getProblemDescription(
        element: KtExpression,
        context: Unit,
    ): @InspectionMessage String = KotlinBundle.message("assigned.value.is.never.read")

    override fun getProblemHighlightType(element: KtExpression, context: Unit): ProblemHighlightType =
        ProblemHighlightType.LIKE_UNUSED_SYMBOL

    override fun createQuickFix(
        element: KtExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtExpression>? = null // KTIJ-29530

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = expressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}
