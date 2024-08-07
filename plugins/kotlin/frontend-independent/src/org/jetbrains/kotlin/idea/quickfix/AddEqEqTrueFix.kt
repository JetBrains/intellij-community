// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class AddEqEqTrueFix(expression: KtExpression) : PsiUpdateModCommandAction<KtExpression>(expression) {

    override fun getFamilyName() = KotlinBundle.message("fix.add.eq.eq.true")

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater,
    ) {
        element.replace(KtPsiFactory(actionContext.project).createExpressionByPattern("$0 == true", element))
    }
}
