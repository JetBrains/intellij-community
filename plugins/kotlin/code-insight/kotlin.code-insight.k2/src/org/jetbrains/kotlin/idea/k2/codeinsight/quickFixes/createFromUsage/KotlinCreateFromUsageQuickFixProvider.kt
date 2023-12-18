// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.psi.PsiReference

class KotlinCreateFromUsageQuickFixProvider: UnresolvedReferenceQuickFixProvider<PsiReference>() {
    override fun registerFixes(ref: PsiReference, registrar: QuickFixActionRegistrar) {
        // TODO: Add other cases like creating a class. Currently, it handles only the creation of callables.
        generateCreateKotlinCallableActions(ref).forEach(registrar::register)
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}