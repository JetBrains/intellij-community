// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.usages

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.JavaChangeInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeInfo

class KotlinWrapperForJavaUsageInfos(
    val kotlinChangeInfo: KotlinChangeInfo,
    val javaChangeInfo: JavaChangeInfo,
    val javaUsageInfos: Array<UsageInfo>,
    primaryMethod: PsiElement
) : UsageInfo(primaryMethod) {
    override fun hashCode() = javaChangeInfo.method.hashCode()

    override fun equals(other: Any?): Boolean {
        return other === this || (other is KotlinWrapperForJavaUsageInfos && javaChangeInfo.method == other.javaChangeInfo.method)
    }
}
