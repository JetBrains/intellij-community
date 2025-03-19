// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix.Kind
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtTypeReference

internal object AddUnsafeVarianceAnnotationFixFactory :
    QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
    override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
        val typeReference = psiElement.parent as? KtTypeReference ?: return emptyList()
        return listOf(
            AddAnnotationFix(
                typeReference,
                ClassId.topLevel(StandardNames.FqNames.unsafeVariance),
                Kind.Self,
            ).asIntention()
        )
    }
}
