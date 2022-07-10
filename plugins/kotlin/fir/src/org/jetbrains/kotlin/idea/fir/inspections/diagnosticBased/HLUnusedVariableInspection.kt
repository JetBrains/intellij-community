// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.inspections.diagnosticBased

import org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix
import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.KotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.AbstractKotlinDiagnosticBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.KotlinApplicatorInputByDiagnosticProvider
import org.jetbrains.kotlin.idea.codeinsight.api.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.KotlinApplicatorPresentation
import org.jetbrains.kotlin.idea.codeinsight.api.presentation
import org.jetbrains.kotlin.idea.codeinsight.api.inputByDiagnosticProvider
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.util.isExplicitTypeReferenceNeededForTypeInference
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty

class HLUnusedVariableInspection :
    AbstractKotlinDiagnosticBasedInspection<KtNamedDeclaration, KtFirDiagnostic.UnusedVariable, KotlinApplicatorInput.Empty>(
        elementType = KtNamedDeclaration::class,
        diagnosticType = KtFirDiagnostic.UnusedVariable::class,
    ) {
    override val inputByDiagnosticProvider: KotlinApplicatorInputByDiagnosticProvider<KtNamedDeclaration, KtFirDiagnostic.UnusedVariable, KotlinApplicatorInput.Empty>
        get() = inputByDiagnosticProvider { diagnostic ->
            val ktProperty = diagnostic.psi as? KtProperty ?: return@inputByDiagnosticProvider null
            if (ktProperty.isExplicitTypeReferenceNeededForTypeInference()) return@inputByDiagnosticProvider null
            KotlinApplicatorInput.Empty
        }
    override val presentation: KotlinApplicatorPresentation<KtNamedDeclaration>
        get() = presentation {
            highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }
    override val applicabilityRange: KotlinApplicabilityRange<KtNamedDeclaration>
        get() = ApplicabilityRanges.DECLARATION_NAME
    override val applicator: KotlinApplicator<KtNamedDeclaration, KotlinApplicatorInput.Empty>
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