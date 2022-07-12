// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ModifierApplicators
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

class HLRedundantVisibilityModifierInspection :
    AbstractKotlinDiagnosticBasedInspection<KtModifierListOwner, KtFirDiagnostic.RedundantVisibilityModifier, ModifierApplicators.Modifier>(
        elementType = KtModifierListOwner::class,
        diagnosticType = KtFirDiagnostic.RedundantVisibilityModifier::class
    ) {

    override val inputByDiagnosticProvider =
        inputByDiagnosticProvider<KtModifierListOwner, KtFirDiagnostic.RedundantVisibilityModifier, ModifierApplicators.Modifier> { diagnostic ->
            val modifier = diagnostic.psi.visibilityModifierType() ?: return@inputByDiagnosticProvider null
            ModifierApplicators.Modifier(modifier)
        }

    override val presentation: KotlinApplicatorPresentation<KtModifierListOwner> = presentation {
        highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
    }

    override val applicabilityRange: KotlinApplicabilityRange<KtModifierListOwner> = ApplicabilityRanges.VISIBILITY_MODIFIER

    override val applicator: KotlinApplicator<KtModifierListOwner, ModifierApplicators.Modifier> =
        ModifierApplicators.removeModifierApplicator(
            KtTokens.VISIBILITY_MODIFIERS,
            KotlinBundle.lazyMessage("redundant.visibility.modifier")
        ).with {
            actionName { _, (modifier) -> KotlinBundle.message("remove.redundant.0.modifier", modifier.value) }
        }
}