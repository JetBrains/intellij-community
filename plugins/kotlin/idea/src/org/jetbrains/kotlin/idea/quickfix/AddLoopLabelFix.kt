// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
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

        val jumpWithLabel = KtPsiFactory(project).createExpression(jumpExpression.text + "@" + labelName)
        jumpExpression.replace(jumpWithLabel)

        // TODO(yole) use createExpressionByPattern() once it's available
        if (existingLabelName == null) {
            val labeledLoopExpression = KtPsiFactory(project).createLabeledExpression(labelName)
            labeledLoopExpression.baseExpression!!.replace(element)
            element.replace(labeledLoopExpression)
        }

        // TODO(yole) We should initiate in-place rename for the label here, but in-place rename for labels is not yet implemented
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = diagnostic.psiElement as? KtExpressionWithLabel
            assert(element is KtBreakExpression || element is KtContinueExpression)
            assert((element as? KtLabeledExpression)?.getLabelName() == null)
            val loop = element?.getStrictParentOfType<KtLoopExpression>() ?: return null
            return AddLoopLabelFix(loop, element)
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
    }
}