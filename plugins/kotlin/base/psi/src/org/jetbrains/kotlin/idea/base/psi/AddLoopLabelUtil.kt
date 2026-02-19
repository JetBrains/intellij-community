// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.psi


import com.intellij.util.text.UniqueNameGenerator
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.parents

object AddLoopLabelUtil {
    fun getUniqueLabelName(loop: KtLoopExpression): String {
        val nameGenerator = UniqueNameGenerator()
        collectUsedLabels(loop, nameGenerator)
        return nameGenerator.generateUniqueName("loop")
    }

    private fun collectUsedLabels(element: KtElement, nameGenerator: UniqueNameGenerator) {
        element.acceptChildren(object : KtTreeVisitorVoid() {
            override fun visitLabeledExpression(expression: KtLabeledExpression) {
                super.visitLabeledExpression(expression)
                nameGenerator.addExistingName(expression.getLabelName()!!)
            }
        })
        element.parents.forEach {
            if (it is KtLabeledExpression) {
                nameGenerator.addExistingName(it.getLabelName()!!)
            }
        }
    }

    fun getExistingLabelName(loop: KtLoopExpression): String? =
        (loop.parent as? KtLabeledExpression)?.getLabelName()
}
