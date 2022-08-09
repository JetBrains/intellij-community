// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsight.utils.isExplicitTypeReferenceNeededForTypeInference
import org.jetbrains.kotlin.idea.codeinsight.utils.removeProperty
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import kotlin.reflect.KClass

internal class UnusedVariableInspection :
    AbstractKotlinDiagnosticBasedInspection<KtNamedDeclaration, KtFirDiagnostic.UnusedVariable, KotlinApplicatorInput.Empty>(
        elementType = KtNamedDeclaration::class,
    ) {
    override fun getDiagnosticType() = KtFirDiagnostic.UnusedVariable::class

    override fun getInputByDiagnosticProvider() =
        inputByDiagnosticProvider<_, KtFirDiagnostic.UnusedVariable, _> { diagnostic ->
            val ktProperty = diagnostic.psi as? KtProperty ?: return@inputByDiagnosticProvider null
            if (ktProperty.isExplicitTypeReferenceNeededForTypeInference()) return@inputByDiagnosticProvider null
            KotlinApplicatorInput
        }

    override fun getApplicabilityRange() = ApplicabilityRanges.DECLARATION_NAME
    override fun getApplicator() = applicator<KtNamedDeclaration, KotlinApplicatorInput.Empty> {
        familyName(KotlinBundle.lazyMessage("remove.element"))
        actionName { psi, _ ->
            KotlinBundle.message("remove.variable.0", psi.name.toString())
        }
        applyTo { psi, _ ->
            removeProperty(psi as KtProperty)
        }
    }
}