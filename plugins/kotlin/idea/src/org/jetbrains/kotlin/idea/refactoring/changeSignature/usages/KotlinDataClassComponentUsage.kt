// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.usages

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class KotlinDataClassComponentUsage(
    calleeExpression: KtSimpleNameExpression,
    private val newName: String
) : KotlinUsageInfo<KtSimpleNameExpression>(calleeExpression) {
    override fun processUsage(changeInfo: KotlinChangeInfo, element: KtSimpleNameExpression, allUsages: Array<out UsageInfo>): Boolean {
        element.replace(KtPsiFactory(element.project).createExpression(newName))
        return true
    }
}