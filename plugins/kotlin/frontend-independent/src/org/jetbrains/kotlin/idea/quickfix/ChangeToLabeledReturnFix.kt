// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ChangeToLabeledReturnFix(
    element: KtReturnExpression,
    private val labeledReturn: String,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtReturnExpression, Unit>(element, Unit) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtReturnExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val factory = KtPsiFactory(actionContext.project)
        val returnedExpression = element.returnedExpression
        val newExpression = if (returnedExpression == null)
            factory.createExpression(labeledReturn)
        else
            factory.createExpressionByPattern("$0 $1", labeledReturn, returnedExpression)
        element.replace(newExpression)
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.change.to.labeled.return.family")

    override fun getActionName(
        actionContext: ActionContext,
        element: KtReturnExpression,
        elementContext: Unit,
    ): String = KotlinBundle.message("fix.change.to.labeled.return.text", labeledReturn)
}
