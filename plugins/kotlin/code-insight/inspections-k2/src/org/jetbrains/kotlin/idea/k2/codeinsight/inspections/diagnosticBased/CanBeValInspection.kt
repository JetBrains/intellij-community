// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KClass

internal class CanBeValInspection : KotlinDiagnosticBasedInspectionBase<KtDeclaration, KaFirDiagnostic.CanBeVal, Unit>() {
    override val diagnosticType: KClass<KaFirDiagnostic.CanBeVal>
        get() = KaFirDiagnostic.CanBeVal::class

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitDeclaration(dcl: KtDeclaration) {
            if (dcl !is KtProperty && dcl !is KtDestructuringDeclaration) return
            visitTargetElement(dcl, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtDeclaration,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("variable.is.never.modified.and.can.be.declared.immutable.using.val")

    override fun getApplicableRanges(element: KtDeclaration): List<TextRange> {
        return ApplicabilityRange.single(element) { (it as? KtValVarKeywordOwner)?.valOrVarKeyword }
    }

    context(KaSession@KaSession)
    override fun prepareContextByDiagnostic(
        element: KtDeclaration,
        diagnostic: KaFirDiagnostic.CanBeVal
    ): Unit? {
        return if (element is KtValVarKeywordOwner) Unit else null
    }

    override fun createQuickFix(
        element: KtDeclaration,
        context: Unit
    ): KotlinModCommandQuickFix<KtDeclaration> = object : KotlinModCommandQuickFix<KtDeclaration>() {

        override fun getFamilyName(): String = KotlinBundle.message("change.to.val")

        override fun applyFix(
            project: Project,
            element: KtDeclaration,
            updater: ModPsiUpdater
        ) {
            val varKeyword = (element as? KtValVarKeywordOwner)?.valOrVarKeyword ?: return
            varKeyword.replace(
                KtPsiFactory(project).createValKeyword()
            )
        }
    }
}
