// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.Variance

class RemoveTypeVarianceFix(
    element: KtTypeParameter,
    private val variance: Variance,
    private val type: String
) : PsiUpdateModCommandAction<KtTypeParameter>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("remove.0.variance.from.1", variance.label, type)

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeParameter,
        updater: ModPsiUpdater,
    ) {
        when (variance) {
            Variance.IN_VARIANCE -> KtTokens.IN_KEYWORD
            Variance.OUT_VARIANCE -> KtTokens.OUT_KEYWORD
            else -> null
        }?.let {
            element.removeModifier(it)
        }
    }
}