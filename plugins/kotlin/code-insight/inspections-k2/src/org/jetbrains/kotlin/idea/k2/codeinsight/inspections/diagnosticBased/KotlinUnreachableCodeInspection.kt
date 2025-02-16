// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinKtDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import kotlin.reflect.KClass

class KotlinUnreachableCodeInspection : KotlinKtDiagnosticBasedInspectionBase<KtElement, KaFirDiagnostic.UnreachableCode, Unit>() {
    override val diagnosticType: KClass<KaFirDiagnostic.UnreachableCode>
        get() = KaFirDiagnostic.UnreachableCode::class

    override fun KaSession.prepareContextByDiagnostic(
        element: KtElement,
        diagnostic: KaFirDiagnostic.UnreachableCode
    ): Unit = Unit

    override fun getProblemDescription(
        element: KtElement,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("inspection.unreachable.code")

    override fun createQuickFix(
        element: KtElement,
        context: Unit
    ): KotlinModCommandQuickFix<KtElement> = object : KotlinModCommandQuickFix<KtElement>() {

        override fun getFamilyName(): String = KotlinBundle.message("inspection.unreachable.code.remove.unreachable.code")

        override fun applyFix(
            project: Project,
            element: KtElement,
            updater: ModPsiUpdater,
        ) {
            element.delete()
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitKtElement(element: KtElement) {
            visitTargetElement(element, holder, isOnTheFly)
        }
    }

}