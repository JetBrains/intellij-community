// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.RedundantModifierInspectionBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.modalityModifierType

class RedundantModalityModifierInspection :
    RedundantModifierInspectionBase<KtFirDiagnostic.RedundantModalityModifier>(KtTokens.MODALITY_MODIFIERS) {

    override fun getActionFamilyName(): String = KotlinBundle.message("redundant.modality.modifier")

    override fun getDiagnosticType() = KtFirDiagnostic.RedundantModalityModifier::class

    override fun getApplicabilityRange() = ApplicabilityRanges.MODALITY_MODIFIER

    context(KtAnalysisSession)
    override fun prepareContextByDiagnostic(
        element: KtModifierListOwner,
        diagnostic: KtFirDiagnostic.RedundantModalityModifier
    ): ModifierContext? = when(element) {
        is KtDeclaration -> element.modalityModifierType()?.let { ModifierContext(it) }
        else -> null
    }
}