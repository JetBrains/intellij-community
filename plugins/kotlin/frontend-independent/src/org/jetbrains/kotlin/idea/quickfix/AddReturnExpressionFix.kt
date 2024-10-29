// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddReturnExpressionFix(element: KtNamedFunction) : PsiUpdateModCommandAction<KtNamedFunction>(element) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtNamedFunction,
        updater: ModPsiUpdater,
    ) {
        val bodyBlock = element.bodyBlockExpression ?: return
        val rBrace = bodyBlock.rBrace ?: return
        val psiFactory = KtPsiFactory(actionContext.project)
        val returnExpression = psiFactory.createExpression("return TODO(\"${KotlinBundle.message("provide.return.value")}\")")
        val todo = bodyBlock.addBefore(returnExpression, rBrace).safeAs<KtReturnExpression>()?.returnedExpression ?: return
        updater.select(todo)
        updater.moveCaretTo(todo.endOffset)
    }

    override fun getFamilyName(): String = KotlinBundle.message("add.return.expression")
}
