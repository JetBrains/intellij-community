// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddDependencyQuickFixHelper
import org.jetbrains.kotlin.idea.quickfix.fixes.ImportQuickFix
import org.jetbrains.kotlin.psi.KtElement

class KotlinFirUnresolvedReferenceQuickFixProvider : UnresolvedReferenceQuickFixProvider<PsiReference>() {
    override fun registerFixes(reference: PsiReference, registrar: QuickFixActionRegistrar) {
        val ktElement = reference.element as? KtElement ?: return
        for (action in AddDependencyQuickFixHelper.createQuickFix(ktElement)) {
            registrar.register(action)
        }

        analyze(ktElement) {
            for (quickFix in ImportQuickFix.getFixes(ktElement)) {
                registrar.register(quickFix)
            }
        }
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}