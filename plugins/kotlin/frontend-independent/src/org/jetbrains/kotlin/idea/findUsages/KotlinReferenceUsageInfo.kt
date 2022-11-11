// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.psi.PsiReference
import com.intellij.usageView.UsageInfo

class KotlinReferenceUsageInfo(reference: PsiReference) : UsageInfo(reference) {
    private val referenceType = reference::class.java

    override fun getReference(): PsiReference? {
        val element = element ?: return null
        return element.references.singleOrNull { it::class.java == referenceType }
    }
}

class KotlinReferencePreservingUsageInfo(private val reference: PsiReference) : UsageInfo(reference) {
    override fun getReference() = reference
}