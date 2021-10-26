// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.inspections.diagnosticBased

import org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix
import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicator
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.AbstractHLDiagnosticBasedInspection
import org.jetbrains.kotlin.idea.fir.api.HLInputByDiagnosticProvider
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicabilityRange
import org.jetbrains.kotlin.idea.fir.api.applicator.HLPresentation
import org.jetbrains.kotlin.idea.fir.api.applicator.presentation
import org.jetbrains.kotlin.idea.fir.api.inputByDiagnosticProvider
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.util.isExplicitTypeReferenceNeededForTypeInference
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty

class HLUnusedVariableInspection :
    AbstractHLDiagnosticBasedInspection<KtNamedDeclaration, KtFirDiagnostic.UnusedVariable, HLApplicatorInput.Empty>(
        elementType = KtNamedDeclaration::class,
        diagnosticType = KtFirDiagnostic.UnusedVariable::class,
    ) {
    override val inputByDiagnosticProvider: HLInputByDiagnosticProvider<KtNamedDeclaration, KtFirDiagnostic.UnusedVariable, HLApplicatorInput.Empty>
        get() = inputByDiagnosticProvider { diagnostic ->
            val ktProperty = diagnostic.psi as? KtProperty ?: return@inputByDiagnosticProvider null
            if (ktProperty.isExplicitTypeReferenceNeededForTypeInference()) return@inputByDiagnosticProvider null
            HLApplicatorInput.Empty
        }
    override val presentation: HLPresentation<KtNamedDeclaration>
        get() = presentation {
            highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }
    override val applicabilityRange: HLApplicabilityRange<KtNamedDeclaration>
        get() = ApplicabilityRanges.DECLARATION_NAME
    override val applicator: HLApplicator<KtNamedDeclaration, HLApplicatorInput.Empty>
        get() = applicator {
            familyName(KotlinBundle.message("remove.element"))
            actionName { psi, _ ->
                KotlinBundle.message("remove.variable.0", psi.name.toString())
            }
            applyTo { psi, _ ->
                RemovePsiElementSimpleFix.RemoveVariableFactory.removeProperty(psi as KtProperty)
            }
        }
}