// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.psi


import com.intellij.util.text.UniqueNameGenerator
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Utility object providing functionality to manipulate and generate labels for Kotlin expressions.
 */
object AddLabelUtil {
    /**
     * Check existing labels inside the [loop] and creates a unique label name.
     */
    fun getUniqueLabelName(loop: KtLoopExpression): String = getUniqueLabelName(loop, "loop")

    /**
     * Generates a unique label name based on the given base name, ensuring it does not collide
     * with labels already used within the provided Kotlin element's scope.
     */
    fun getUniqueLabelName(element: KtElement, baseName: String): String {
        val nameGenerator = UniqueNameGenerator()
        collectUsedLabels(element) { nameGenerator.addExistingName(it) }
        return nameGenerator.generateUniqueName(baseName)
    }

    fun isLabelNameUnique(element: KtElement, labelName: String): Boolean {
        var usages = 0
        collectUsedLabels(element) {
            if (it == labelName) usages++
        }
        return usages <= 1
    }

    /**
     * Replaces [expression] with a labeled expression with the given label name and same expression as base expression.
     */
    fun addLabel(expression: KtExpression, labelName: String): KtLabeledExpression {
        return expression.replace(
            KtPsiFactory(expression.project).createExpressionByPattern("$0@ $1", labelName, expression, reformat = false)
        ) as KtLabeledExpression
    }

    private fun collectUsedLabels(element: KtElement, addLabelName: (String) -> Unit) {
        element.acceptChildren(object : KtTreeVisitorVoid() {
            override fun visitLabeledExpression(expression: KtLabeledExpression) {
                super.visitLabeledExpression(expression)
                expression.getLabelName()?.let(addLabelName)
            }

            override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                super.visitLambdaExpression(lambdaExpression)
                if (lambdaExpression.parent is KtLabeledExpression) return
                lambdaExpression.functionLiteral.findLabelAndCall().first?.asString()?.let(addLabelName)
            }
        })
        element.parents.forEach {
            when (it) {
                is KtLabeledExpression -> it.getLabelName()?.let(addLabelName)
                is KtLambdaExpression -> {
                    if (it.parent !is KtLabeledExpression) {
                        it.functionLiteral.findLabelAndCall().first?.asString()?.let(addLabelName)
                    }
                }
            }
        }
    }

    fun getExistingLabelName(loop: KtLoopExpression): String? =
        (loop.parent as? KtLabeledExpression)?.getLabelName()
}
