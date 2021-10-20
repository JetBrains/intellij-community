/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ReplaceWithArrayCallInAnnotationFix(argument: KtExpression) : KotlinPsiOnlyQuickFixAction<KtExpression>(argument) {
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

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val element = psiElement as? KtExpression ?: return emptyList()
            return listOf(ReplaceWithArrayCallInAnnotationFix(element))
        }
    }
}
