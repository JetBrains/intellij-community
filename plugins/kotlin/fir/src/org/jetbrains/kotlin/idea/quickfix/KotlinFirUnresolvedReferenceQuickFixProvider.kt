// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddDependencyQuickFixHelper

class KotlinFirUnresolvedReferenceQuickFixProvider : UnresolvedReferenceQuickFixProvider<PsiReference>() {
    override fun registerFixes(reference: PsiReference, registrar: QuickFixActionRegistrar) {
        for (action in AddDependencyQuickFixHelper.createQuickFix(reference.element)) {
            registrar.register(action)
        }
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}