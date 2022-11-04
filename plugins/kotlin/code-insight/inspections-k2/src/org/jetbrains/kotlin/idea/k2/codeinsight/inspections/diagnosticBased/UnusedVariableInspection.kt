// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableDiagnosticInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.isExplicitTypeReferenceNeededForTypeInference
import org.jetbrains.kotlin.idea.codeinsight.utils.removeProperty
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty

internal class UnusedVariableInspection :
    KotlinApplicableDiagnosticInspection<KtNamedDeclaration, KtFirDiagnostic.UnusedVariable>(
        KtNamedDeclaration::class,
    ) {

    override fun getFamilyName(): String = KotlinBundle.message("inspection.kotlin.unused.variable.display.name")
    override fun getActionName(element: KtNamedDeclaration): String =
        KotlinBundle.message("remove.variable.0", element.name.toString())

    override fun getDiagnosticType() = KtFirDiagnostic.UnusedVariable::class

    override fun getApplicabilityRange() = ApplicabilityRanges.DECLARATION_NAME

    context(KtAnalysisSession)
    override fun isApplicableByDiagnostic(element: KtNamedDeclaration, diagnostic: KtFirDiagnostic.UnusedVariable): Boolean {
        val ktProperty = diagnostic.psi as? KtProperty ?: return false
        return !ktProperty.isExplicitTypeReferenceNeededForTypeInference()
    }

    override fun apply(element: KtNamedDeclaration, project: Project, editor: Editor?) {
        val property = element as? KtProperty ?: return
        removeProperty(property)
    }
}