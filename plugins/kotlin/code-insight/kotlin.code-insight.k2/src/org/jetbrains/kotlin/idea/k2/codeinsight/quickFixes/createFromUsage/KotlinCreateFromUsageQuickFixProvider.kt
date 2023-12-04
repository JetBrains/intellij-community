// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement

class KotlinCreateFromUsageQuickFixProvider: UnresolvedReferenceQuickFixProvider<PsiReference>() {
    override fun registerFixes(ref: PsiReference, registrar: QuickFixActionRegistrar) {
        val ktElement = ref.element as? KtElement ?: return
        val parent = ktElement.parent
        if (parent is KtCallExpression) {
            //todo create dedicated fix if all accessible targets are kotlin kotlin KTIJ-27789
            generateCreateMethodActions(parent).forEach(registrar::register)
        }
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}