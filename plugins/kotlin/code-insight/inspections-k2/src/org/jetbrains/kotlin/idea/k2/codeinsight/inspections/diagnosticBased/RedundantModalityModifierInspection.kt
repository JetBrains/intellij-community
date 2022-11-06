// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.CleanupLocalInspectionTool
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ModifierApplicators
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.modalityModifierType
import org.jetbrains.kotlin.psi.KtModifierListOwner

class RedundantModalityModifierInspection :
    AbstractKotlinDiagnosticBasedInspection<KtModifierListOwner, KtFirDiagnostic.RedundantModalityModifier, ModifierApplicators.Modifier>(
        elementType = KtModifierListOwner::class,
    ),
    CleanupLocalInspectionTool {

    override fun getDiagnosticType() = KtFirDiagnostic.RedundantModalityModifier::class

    override fun getInputByDiagnosticProvider() =
        inputByDiagnosticProvider<_, KtFirDiagnostic.RedundantModalityModifier, _> { diagnostic ->
            when(val element = diagnostic.psi) {
                is KtDeclaration -> element.modalityModifierType()?.let { ModifierApplicators.Modifier(it) }
                else -> null
            }
        }

    override fun getApplicabilityRange() = ApplicabilityRanges.MODALITY_MODIFIER

    override fun getApplicator() =
        ModifierApplicators.removeRedundantModifierApplicator(
            KtTokens.MODALITY_MODIFIERS,
            KotlinBundle.lazyMessage("redundant.modality.modifier"),
        )
}