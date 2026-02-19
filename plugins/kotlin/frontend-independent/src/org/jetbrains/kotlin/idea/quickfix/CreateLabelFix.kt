// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addIfNotNull

sealed class CreateLabelFix(
    expression: KtLabelReferenceExpression
) : KotlinQuickFixAction<KtLabelReferenceExpression>(expression) {
    class ForLoop(expression: KtLabelReferenceExpression) : CreateLabelFix(expression) {
        override val chooserTitle: String = KotlinBundle.message("select.loop.statement.to.label")

        override fun getCandidateExpressions(labelReferenceExpression: KtLabelReferenceExpression): List<KtLoopExpression> =
            labelReferenceExpression.getContainingLoops().toList()
    }

    class ForLambda(expression: KtLabelReferenceExpression) : CreateLabelFix(expression) {
        override val chooserTitle: String = KotlinBundle.message("select.lambda.to.label")

        override fun getCandidateExpressions(labelReferenceExpression: KtLabelReferenceExpression): List<KtLambdaExpression> =
            labelReferenceExpression.getContainingLambdas().toList()
    }

    override fun getFamilyName(): String = KotlinBundle.message("create.label")

    override fun getText(): String = KotlinBundle.message("create.label.0", element?.getReferencedName() ?: "")

    abstract val chooserTitle: String

    abstract fun getCandidateExpressions(labelReferenceExpression: KtLabelReferenceExpression): List<KtExpression>

    override fun startInWriteAction(): Boolean = false

    private fun doCreateLabel(expression: KtLabelReferenceExpression, it: KtExpression, project: Project) {
        project.executeWriteCommand(text) {
            it.replace(KtPsiFactory(project).createExpressionByPattern("${expression.getReferencedName()}@ $0", it))
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        if (editor == null) return

        val containers = getCandidateExpressions(expression)

        if (isUnitTestMode()) {
            return doCreateLabel(expression, containers.last(), project)
        }

        chooseContainerElementIfNecessary(
            containers,
            editor,
            chooserTitle,
            true
        ) {
            doCreateLabel(expression, it, project)
        }
    }

    companion object {
        fun KtLabelReferenceExpression.getContainingLoops(): Sequence<KtLoopExpression> {
            return parents
                .takeWhile { !(it is KtDeclarationWithBody || it is KtClassBody || it is KtFile) }
                .filterIsInstance<KtLoopExpression>()
                .filterAvailableForLabel(getReferencedName())
        }

        fun KtLabelReferenceExpression.getContainingLambdas(): Sequence<KtLambdaExpression> {
            return parents
                .takeWhile { !(it is KtDeclarationWithBody && it !is KtFunctionLiteral || it is KtClassBody || it is KtFile) }
                .filterIsInstance<KtLambdaExpression>()
                .filterAvailableForLabel(getReferencedName())
        }

        private fun <T : KtExpression> Sequence<T>.filterAvailableForLabel(labelName: String): Sequence<T> {
            // Optimization: To prevent doubly parsing a PSI tree, we create a list and iterate on a cached list
            val allExpressions = this.toList()

            val existingLabels = mutableSetOf<String>()
            for (expr in allExpressions) {
                val parent = expr.parent
                if (parent is KtLabeledExpression && parent.baseExpression == expr) {
                    existingLabels.addIfNotNull(parent.getLabelName())
                }
            }

            return sequence {
                for (expr in allExpressions) {
                    if (labelName !in existingLabels) {
                        yield(expr)
                    }
                }
            }
        }
    }
}
