// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtWhenExpression

internal object CommaInWhenConditionWithoutArgumentFixFactory : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {

    override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
        return listOfNotNull(
            (psiElement.parent?.parent as? KtWhenExpression)?.let { CommaInWhenConditionWithoutArgumentFix(it).asIntention() }
        )
    }
}
