// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

internal object AddConsistentCopyVisibilityAnnotationFixFactory : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
    override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
        val containingClass = psiElement.parents(withSelf = true).firstIsInstanceOrNull<KtClass>() ?: return emptyList()
        return listOf(AddAnnotationFix(containingClass, StandardClassIds.Annotations.ConsistentCopyVisibility))
    }
}
