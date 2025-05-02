// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.psi


import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.parents

object AddLoopLabelUtil {
    fun getUniqueLabelName(loop: KtLoopExpression): String =
        getUniqueLabelName(collectUsedLabels(loop))

    private fun getUniqueLabelName(existingNames: Collection<String>): String {
        var index = 0
        var result = "loop"
        while (result in existingNames) {
            result = "loop${++index}"
        }
        return result
    }

    private fun collectUsedLabels(element: KtElement): Set<String> {
        val usedLabels = hashSetOf<String>()
        element.acceptChildren(object : KtTreeVisitorVoid() {
            override fun visitLabeledExpression(expression: KtLabeledExpression) {
                super.visitLabeledExpression(expression)
                usedLabels.add(expression.getLabelName()!!)
            }
        })
        element.parents.forEach {
            if (it is KtLabeledExpression) {
                usedLabels.add(it.getLabelName()!!)
            }
        }
        return usedLabels
    }

    fun getExistingLabelName(loop: KtLoopExpression): String? =
        (loop.parent as? KtLabeledExpression)?.getLabelName()
}
