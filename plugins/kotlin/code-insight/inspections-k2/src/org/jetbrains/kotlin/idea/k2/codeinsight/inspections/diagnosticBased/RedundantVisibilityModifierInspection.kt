// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ModifierApplicators
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

internal class RedundantVisibilityModifierInspection :
    AbstractKotlinDiagnosticBasedInspection<KtModifierListOwner, KtFirDiagnostic.RedundantVisibilityModifier, ModifierApplicators.Modifier>(
        elementType = KtModifierListOwner::class,
    ) {

    override fun getDiagnosticType() = KtFirDiagnostic.RedundantVisibilityModifier::class

    override fun getInputByDiagnosticProvider() =
        inputByDiagnosticProvider<_, KtFirDiagnostic.RedundantVisibilityModifier, _> { diagnostic ->
            val modifier = diagnostic.psi.visibilityModifierType() ?: return@inputByDiagnosticProvider null
            ModifierApplicators.Modifier(modifier)
        }

    override fun getApplicabilityRange() = ApplicabilityRanges.VISIBILITY_MODIFIER

    override fun getApplicator() =
        ModifierApplicators.removeRedundantModifierApplicator(
            KtTokens.VISIBILITY_MODIFIERS,
            KotlinBundle.lazyMessage("redundant.visibility.modifier")
        )
}