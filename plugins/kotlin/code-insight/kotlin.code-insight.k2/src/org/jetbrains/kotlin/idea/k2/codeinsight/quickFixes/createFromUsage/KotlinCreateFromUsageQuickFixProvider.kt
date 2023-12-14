// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.psi.KtElement

class KotlinCreateFromUsageQuickFixProvider: UnresolvedReferenceQuickFixProvider<PsiReference>() {
    override fun registerFixes(ref: PsiReference, registrar: QuickFixActionRegistrar) {
        when (val element = ref.element) {
            // Currently, we only support creating Kotlin functions from usage in Kotlin. We can add more cases here like
            // creating Kotlin functions from usage in Java, creating Kotlin classes, and so on.
            is KtElement -> generateCreateKotlinCallableActions(element).forEach(registrar::register)
        }
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}