// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteCustomUsageInfo
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteUsageInfo
import org.jetbrains.kotlin.idea.refactoring.removeOverrideModifier

class KotlinSafeDeleteOverrideAnnotation(
    element: PsiElement, referencedElement: PsiElement
) : SafeDeleteUsageInfo(element, referencedElement), SafeDeleteCustomUsageInfo {
    override fun performRefactoring() {
        element?.removeOverrideModifier()
    }
}
