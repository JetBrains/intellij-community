// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents

class AddLoopLabelFix(
    loop: KtLoopExpression,
    private val jumpExpression: KtExpressionWithLabel
) : KotlinQuickFixAction<KtLoopExpression>(loop) {

    private val existingLabelName = (loop.parent as? KtLabeledExpression)?.getLabelName()

    @Nls
    private val description = run {
        when {
            existingLabelName != null -> {
                val labelName = "@$existingLabelName"
                KotlinBundle.message("fix.add.loop.label.text", labelName, jumpExpression.text)
            }
            else -> {
                KotlinBundle.message("fix.add.loop.label.text.generic")
            }
        }
    }

    override fun getText() = description
    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val labelName = existingLabelName ?: getUniqueLabelName(element)

        jumpExpression.replace(KtPsiFactory(project).createExpression(jumpExpression.text + "@" + labelName))

        if (existingLabelName == null) {
            element.replace(KtPsiFactory(project).createExpressionByPattern("$0@ $1", labelName, element, reformat = false))
        }

        // TODO(yole) We should initiate in-place rename for the label here, but in-place rename for labels is not yet implemented
    }

    companion object {
        private fun getUniqueLabelName(existingNames: Collection<String>): String {
            var index = 0
            var result = "loop"
            while (result in existingNames) {
                result = "loop${++index}"
            }
            return result
        }

        fun getUniqueLabelName(loop: KtLoopExpression): String =
            getUniqueLabelName(collectUsedLabels(loop))

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
    }
}
