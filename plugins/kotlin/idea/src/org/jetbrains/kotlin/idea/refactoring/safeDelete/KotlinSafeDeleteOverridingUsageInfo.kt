// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteCustomUsageInfo
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteUsageInfo
import org.jetbrains.kotlin.psi.KtPsiUtil

class KotlinSafeDeleteOverridingUsageInfo(
    overridingElement: PsiElement, superElement: PsiElement
) : SafeDeleteUsageInfo(overridingElement, superElement), SafeDeleteCustomUsageInfo {
    val overridingElement: PsiElement get() = element!!

    override fun performRefactoring(): Unit {
        KtPsiUtil.ascendIfPropertyAccessor(element)?.delete()
    }
}
