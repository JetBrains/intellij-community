// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ReplaceWithArrayCallInAnnotationFix(argument: KtExpression) : KotlinQuickFixAction<KtExpression>(argument) {
    override fun getText() = KotlinBundle.message("replace.with.array.call")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val argument = element?.getParentOfType<KtValueArgument>(false) ?: return
        val spreadElement = argument.getSpreadElement()
        if (spreadElement != null)
            spreadElement.delete()
        else
            surroundWithArrayLiteral(argument)
    }

    private fun surroundWithArrayLiteral(argument: KtValueArgument) {
        val argumentExpression = argument.getArgumentExpression() ?: return
        val factory = KtPsiFactory(argumentExpression)
        val surrounded = factory.createExpressionByPattern("[$0]", argumentExpression)

        argumentExpression.replace(surrounded)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtExpression>? {
            val element = diagnostic.psiElement.safeAs<KtExpression>() ?: return null
            return ReplaceWithArrayCallInAnnotationFix(element)
        }
    }
}
