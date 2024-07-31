// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createValueArgumentListByPattern

class AddEmptyArgumentListFix(element: KtCallExpression) : PsiUpdateModCommandAction<KtCallExpression>(element) {
    override fun getFamilyName(): String = KotlinBundle.message("add.empty.argument.list")

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        updater: ModPsiUpdater,
    ) {
        val calleeExpression = element.calleeExpression ?: return
        val emptyArgumentList = KtPsiFactory(actionContext.project).createValueArgumentListByPattern("()")
        element.addAfter(emptyArgumentList, calleeExpression)
    }
}
