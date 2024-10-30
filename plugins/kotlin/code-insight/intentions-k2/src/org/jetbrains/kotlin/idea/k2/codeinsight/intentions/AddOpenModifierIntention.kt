// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class AddOpenModifierIntention :
    KotlinApplicableModCommandAction<KtCallableDeclaration, Unit>(KtCallableDeclaration::class) {
    override fun getFamilyName(): String =
        KotlinBundle.message("make.open")

    override fun getPresentation(context: ActionContext, element: KtCallableDeclaration): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun isApplicableByPsi(element: KtCallableDeclaration): Boolean =
        (element is KtProperty || element is KtNamedFunction)
                && !element.hasModifier(KtTokens.OPEN_KEYWORD)
                && !element.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                && !element.hasModifier(KtTokens.PRIVATE_KEYWORD)

    context(KaSession)
    override fun prepareContext(element: KtCallableDeclaration): Unit? {
        // The intention's applicability cannot solely depend on the PSI because compiler plugins may introduce modality different from
        // explicit syntax and language defaults.
        val elementSymbol = element.symbol
        if (elementSymbol.modality == KaSymbolModality.OPEN || elementSymbol.modality == KaSymbolModality.ABSTRACT) {
            return null
        }

        val owner = element.containingClassOrObject ?: return null
        val ownerSymbol = owner.symbol
        val isApplicable = (owner.hasModifier(KtTokens.ENUM_KEYWORD)
                || ownerSymbol.modality == KaSymbolModality.OPEN
                || ownerSymbol.modality == KaSymbolModality.ABSTRACT
                || ownerSymbol.modality == KaSymbolModality.SEALED)
        return isApplicable.asUnit
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtCallableDeclaration,
      elementContext: Unit,
      updater: ModPsiUpdater,
    ) {
        element.addModifier(KtTokens.OPEN_KEYWORD)
    }
}