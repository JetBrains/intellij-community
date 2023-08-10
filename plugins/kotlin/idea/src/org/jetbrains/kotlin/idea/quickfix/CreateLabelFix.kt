// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents

sealed class CreateLabelFix(
    expression: KtLabelReferenceExpression
) : KotlinQuickFixAction<KtLabelReferenceExpression>(expression) {
    class ForLoop(expression: KtLabelReferenceExpression) : CreateLabelFix(expression) {
        override val chooserTitle = KotlinBundle.message("select.loop.statement.to.label")

        override fun getCandidateExpressions(labelReferenceExpression: KtLabelReferenceExpression) =
            labelReferenceExpression.getContainingLoops().toList()
    }

    class ForLambda(expression: KtLabelReferenceExpression) : CreateLabelFix(expression) {
        override val chooserTitle = KotlinBundle.message("select.lambda.to.label")

        override fun getCandidateExpressions(labelReferenceExpression: KtLabelReferenceExpression) =
            labelReferenceExpression.getContainingLambdas().toList()
    }

    override fun getFamilyName() = KotlinBundle.message("create.label")

    override fun getText() = KotlinBundle.message("create.label.0", element?.getReferencedName() ?: "")

    abstract val chooserTitle: String

    abstract fun getCandidateExpressions(labelReferenceExpression: KtLabelReferenceExpression): List<KtExpression>

    override fun startInWriteAction() = false

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

    companion object : KotlinSingleIntentionActionFactory() {
        private fun KtLabelReferenceExpression.getContainingLoops(): Sequence<KtLoopExpression> {
            return parents
                .takeWhile { !(it is KtDeclarationWithBody || it is KtClassBody || it is KtFile) }
                .filterIsInstance<KtLoopExpression>()
        }

        private fun KtLabelReferenceExpression.getContainingLambdas(): Sequence<KtLambdaExpression> {
            return parents
                .takeWhile { !(it is KtDeclarationWithBody && it !is KtFunctionLiteral || it is KtClassBody || it is KtFile) }
                .filterIsInstance<KtLambdaExpression>()
        }

        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val labelReferenceExpression = diagnostic.psiElement as? KtLabelReferenceExpression ?: return null
            return when ((labelReferenceExpression.parent as? KtContainerNode)?.parent) {
                is KtBreakExpression, is KtContinueExpression -> {
                    if (labelReferenceExpression.getContainingLoops().any()) ForLoop(labelReferenceExpression) else null
                }
                is KtReturnExpression -> {
                    if (labelReferenceExpression.getContainingLambdas().any()) ForLambda(labelReferenceExpression) else null
                }
                else -> null
            }
        }
    }
}