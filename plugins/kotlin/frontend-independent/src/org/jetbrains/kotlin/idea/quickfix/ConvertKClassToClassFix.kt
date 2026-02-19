// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ConvertKClassToClassFix(element: KtExpression) : PsiUpdateModCommandAction<KtExpression>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.from.class.to.kclass")
    override fun getPresentation(context: ActionContext, element: KtExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.HIGH)

    override fun invoke(
        context: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater,
    ) {
        val expressionToInsert = KtPsiFactory(context.project).createExpressionByPattern("$0.java", element)
        element.replaced(expressionToInsert)
    }
}
