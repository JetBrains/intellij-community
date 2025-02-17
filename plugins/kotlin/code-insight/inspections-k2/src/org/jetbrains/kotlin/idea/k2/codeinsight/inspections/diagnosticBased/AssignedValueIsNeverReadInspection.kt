// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinPsiDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.simpleNameExpressionVisitor
import kotlin.reflect.KClass

internal class AssignedValueIsNeverReadInspection :
    KotlinPsiDiagnosticBasedInspectionBase<KtSimpleNameExpression, KaFirDiagnostic.AssignedValueIsNeverRead, Unit>() {
    override val diagnosticType: KClass<KaFirDiagnostic.AssignedValueIsNeverRead>
        get() = KaFirDiagnostic.AssignedValueIsNeverRead::class

    override fun KaSession.prepareContextByDiagnostic(
        element: KtSimpleNameExpression,
        diagnostic: KaFirDiagnostic.AssignedValueIsNeverRead,
    ): Unit = Unit

    override fun getProblemDescription(
        element: KtSimpleNameExpression,
        context: Unit,
    ): @InspectionMessage String = KotlinBundle.message("assigned.value.is.never.read")

    override fun getProblemHighlightType(element: KtSimpleNameExpression, context: Unit): ProblemHighlightType =
        ProblemHighlightType.LIKE_UNUSED_SYMBOL

    override fun createQuickFix(
        element: KtSimpleNameExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtSimpleNameExpression> =
        object : KotlinModCommandQuickFix<KtSimpleNameExpression>() {
            override fun getFamilyName(): String = KotlinBundle.message("remove.redundant.assignment")
            override fun applyFix(
                project: Project,
                element: KtSimpleNameExpression,
                updater: ModPsiUpdater
            ) {
                val parent = element.parent as? KtBinaryExpression ?: return
                parent.delete()
            }
        }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = simpleNameExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}
