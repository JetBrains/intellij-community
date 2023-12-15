// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class AddToStringFix(element: KtExpression, private val useSafeCallOperator: Boolean) :
    PsiUpdateModCommandAction<KtExpression>(element), LowPriorityAction {
    override fun getFamilyName(): String = KotlinBundle.message("fix.add.tostring.call.family")

    override fun getPresentation(context: ActionContext, element: KtExpression): Presentation =
        Presentation.of(
            when (useSafeCallOperator) {
                true -> KotlinBundle.message("fix.add.tostring.call.text.safe")
                false -> KotlinBundle.message("fix.add.tostring.call.text")
            }
        )

    override fun invoke(context: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
        val pattern = if (useSafeCallOperator) "$0?.toString()" else "$0.toString()"
        val expressionToInsert = KtPsiFactory(element.project).createExpressionByPattern(pattern, element)
        val newExpression = element.replaced(expressionToInsert)
        updater.moveCaretTo(newExpression.endOffset)
    }
}