// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.RedundantModifierInspectionBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import kotlin.reflect.KClass

internal class RedundantVisibilityModifierInspection :
    RedundantModifierInspectionBase<KtFirDiagnostic.RedundantVisibilityModifier>(KtTokens.VISIBILITY_MODIFIERS) {

    override fun createQuickFix(
        element: KtModifierListOwner,
        context: ModifierContext,
    ): KotlinModCommandQuickFix<KtModifierListOwner> = object : RemoveRedundantModifierQuickFixBase(context) {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.redundant.visibility.modifier")
    }

    override val diagnosticType: KClass<KtFirDiagnostic.RedundantVisibilityModifier>
        get() = KtFirDiagnostic.RedundantVisibilityModifier::class

    override fun getApplicableRanges(element: KtModifierListOwner): List<TextRange> =
        ApplicabilityRanges.visibilityModifier(element)

    context(KtAnalysisSession)
    override fun prepareContextByDiagnostic(
        element: KtModifierListOwner,
        diagnostic: KtFirDiagnostic.RedundantVisibilityModifier,
    ): ModifierContext? {
        val modifier = element.visibilityModifierType() ?: return null
        return ModifierContext(modifier)
    }
}