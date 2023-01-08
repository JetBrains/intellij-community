// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class AddOpenModifierIntention :
    AbstractKotlinApplicatorBasedIntention<KtCallableDeclaration, KotlinApplicatorInput.Empty>(KtCallableDeclaration::class),
    LowPriorityAction {
    override fun getApplicator() = applicator<KtCallableDeclaration, KotlinApplicatorInput.Empty> {
        familyAndActionName(KotlinBundle.lazyMessage("make.open"))

        isApplicableByPsi { element ->
            (element is KtProperty || element is KtNamedFunction)
                    && !element.hasModifier(KtTokens.OPEN_KEYWORD)
                    && !element.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                    && !element.hasModifier(KtTokens.PRIVATE_KEYWORD)
        }

        applyTo { element, _ ->
            element.addModifier(KtTokens.OPEN_KEYWORD)
        }
    }

    override fun getApplicabilityRange() = ApplicabilityRanges.SELF

    /**
     * The intention's applicability cannot solely depend on the PSI because compiler plugins may introduce modality different from
     * explicit syntax and language defaults.
     */
    override fun getInputProvider() = inputProvider { element: KtCallableDeclaration ->
        val elementSymbol = element.getSymbol() as? KtSymbolWithModality ?: return@inputProvider null
        if (elementSymbol.modality == Modality.OPEN || elementSymbol.modality == Modality.ABSTRACT) {
            return@inputProvider null
        }

        val owner = element.containingClassOrObject ?: return@inputProvider null
        val ownerSymbol = owner.getSymbol() as? KtSymbolWithModality ?: return@inputProvider null
        if (
            owner.hasModifier(KtTokens.ENUM_KEYWORD)
            || ownerSymbol.modality == Modality.OPEN
            || ownerSymbol.modality == Modality.ABSTRACT
            || ownerSymbol.modality == Modality.SEALED
        ) KotlinApplicatorInput.Empty else null
    }
}