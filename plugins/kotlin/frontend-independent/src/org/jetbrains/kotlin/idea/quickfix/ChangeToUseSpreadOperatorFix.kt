// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

class ChangeToUseSpreadOperatorFix(element: KtExpression) : PsiUpdateModCommandAction<KtExpression>(element) {

    override fun getFamilyName(): String =
        KotlinBundle.message("fix.change.to.use.spread.operator.family")

    override fun getPresentation(
        context: ActionContext,
        element: KtExpression,
    ): Presentation = Presentation.of(
        KotlinBundle.message("fix.change.to.use.spread.operator.text", element.text, "*${element.text}")
    )

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater,
    ) {
        val star = KtPsiFactory(actionContext.project).createStar()
        element.parent.addBefore(star, element)
    }
}