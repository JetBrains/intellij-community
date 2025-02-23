// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.RedundantModifierInspectionBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import kotlin.reflect.KClass

internal class RedundantVisibilityModifierInspection :
    RedundantModifierInspectionBase<KaFirDiagnostic.RedundantVisibilityModifier>(KtTokens.VISIBILITY_MODIFIERS) {

    override fun createQuickFix(
        element: KtModifierListOwner,
        context: ModifierContext,
    ): KotlinModCommandQuickFix<KtModifierListOwner> = object : RemoveRedundantModifierQuickFixBase(context) {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.redundant.visibility.modifier")
    }

    override val diagnosticType: KClass<KaFirDiagnostic.RedundantVisibilityModifier>
        get() = KaFirDiagnostic.RedundantVisibilityModifier::class

    override fun getApplicableRanges(element: KtModifierListOwner): List<TextRange> =
        ApplicabilityRanges.visibilityModifier(element)

    override fun KaSession.prepareContextByDiagnostic(
        element: KtModifierListOwner,
        diagnostic: KaFirDiagnostic.RedundantVisibilityModifier,
    ): ModifierContext? {
        val modifier = element.visibilityModifierType() ?: return null
        return ModifierContext(modifier)
    }
}