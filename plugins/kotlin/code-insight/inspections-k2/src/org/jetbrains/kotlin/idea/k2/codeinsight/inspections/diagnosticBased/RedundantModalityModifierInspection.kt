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
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.modalityModifierType
import kotlin.reflect.KClass

internal class RedundantModalityModifierInspection :
    RedundantModifierInspectionBase<KaFirDiagnostic.RedundantModalityModifier>(KtTokens.MODALITY_MODIFIERS) {

    override fun createQuickFix(
        element: KtModifierListOwner,
        context: ModifierContext,
    ): KotlinModCommandQuickFix<KtModifierListOwner> = object : RemoveRedundantModifierQuickFixBase(context) {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.redundant.modality.modifier")
    }

    override val diagnosticType: KClass<KaFirDiagnostic.RedundantModalityModifier>
        get() = KaFirDiagnostic.RedundantModalityModifier::class

    override fun getApplicableRanges(element: KtModifierListOwner): List<TextRange> =
        ApplicabilityRanges.modalityModifier(element)

    override fun KaSession.prepareContextByDiagnostic(
        element: KtModifierListOwner,
        diagnostic: KaFirDiagnostic.RedundantModalityModifier,
    ): ModifierContext? = when (element) {
        is KtDeclaration -> element.modalityModifierType()?.let { ModifierContext(it) }
        else -> null
    }
}