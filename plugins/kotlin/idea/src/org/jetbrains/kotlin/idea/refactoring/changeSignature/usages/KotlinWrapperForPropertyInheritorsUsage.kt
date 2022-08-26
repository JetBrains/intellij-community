// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.usages

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeInfo

class KotlinWrapperForPropertyInheritorsUsage(
    val propertyChangeInfo: KotlinChangeInfo,
    val originalUsageInfo: UsageInfo,
    element: PsiElement,
) : UsageInfo(element) {
    override fun getElement(): PsiElement? = originalUsageInfo.element

    override fun equals(other: Any?): Boolean = this === other ||
            (other is KotlinWrapperForPropertyInheritorsUsage &&
                    originalUsageInfo == other.originalUsageInfo &&
                    propertyChangeInfo == other.propertyChangeInfo)

    override fun hashCode(): Int = originalUsageInfo.hashCode()
}

val UsageInfo.unwrapped: UsageInfo get() = if (this is KotlinWrapperForPropertyInheritorsUsage) originalUsageInfo else this
