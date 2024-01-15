// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableDiagnosticInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.isExplicitTypeReferenceNeededForTypeInference
import org.jetbrains.kotlin.idea.codeinsight.utils.removeProperty
import org.jetbrains.kotlin.idea.codeinsight.utils.renameToUnderscore
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*

internal class UnusedVariableInspection
  : AbstractKotlinApplicableInspection<KtNamedDeclaration>(), AbstractKotlinApplicableDiagnosticInspection<KtNamedDeclaration, KtFirDiagnostic.UnusedVariable> {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                visitTargetElement(declaration, holder, isOnTheFly)
            }
        }
    }
    override fun getProblemDescription(element: KtNamedDeclaration): String =
        KotlinBundle.message("inspection.kotlin.unused.variable.display.name")

    override fun getActionFamilyName(): String = KotlinBundle.message("remove.variable")

    override fun getActionName(element: KtNamedDeclaration): String =
        if (element is KtDestructuringDeclarationEntry) KotlinBundle.message("rename.to.underscore")
        else KotlinBundle.message("remove.variable.0", element.name.toString())

    override fun getDiagnosticType() = KtFirDiagnostic.UnusedVariable::class

    override fun getApplicabilityRange() = ApplicabilityRanges.DECLARATION_NAME

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtNamedDeclaration): Boolean {
        val diagnostics = element.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
        val suitableDiagnostics = diagnostics.filterIsInstance(getDiagnosticType().java)
        val diagnostic = suitableDiagnostics.firstOrNull() ?: return false
        val ktProperty = diagnostic.psi as? KtCallableDeclaration ?: return false
        val typeReference = ktProperty.typeReference ?: return true
        return !ktProperty.isExplicitTypeReferenceNeededForTypeInference(typeReference)
    }

    override fun apply(element: KtNamedDeclaration, project: Project, updater: ModPsiUpdater) {
        if (element is KtDestructuringDeclarationEntry) {
            renameToUnderscore(element)
        }
        else if (element is KtProperty) {
            removeProperty(element)
        }
    }
}