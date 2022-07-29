// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddReturnExpressionFix(element: KtNamedFunction) : KotlinQuickFixAction<KtNamedFunction>(element) {
    override fun getText() = KotlinBundle.message("add.return.expression")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val bodyBlock = element?.bodyBlockExpression ?: return
        val rBrace = bodyBlock.rBrace ?: return
        val returnExpression =
            KtPsiFactory(project).createExpression("return TODO(\"${KotlinBundle.message("provide.return.value")}\")")
        val todo = bodyBlock.addBefore(returnExpression, rBrace).safeAs<KtReturnExpression>()?.returnedExpression
        if (todo != null && editor != null) {
            editor.selectionModel.setSelection(todo.startOffset, todo.endOffset)
            editor.caretModel.moveToOffset(todo.endOffset)
        }
    }

    companion object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = Errors.NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY.cast(diagnostic).psiElement
            val function = element.safeAs<KtNamedFunction>() ?: return null
            return AddReturnExpressionFix(function)
        }
    }
}
