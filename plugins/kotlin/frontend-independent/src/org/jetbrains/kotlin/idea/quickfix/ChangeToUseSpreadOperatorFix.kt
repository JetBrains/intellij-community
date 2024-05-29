// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

class ChangeToUseSpreadOperatorFix(
    element: KtExpression,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, Unit>(element, Unit) {

    override fun getFamilyName() = KotlinBundle.message("fix.change.to.use.spread.operator.family")

    override fun getActionName(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: Unit,
    ): String = KotlinBundle.message("fix.change.to.use.spread.operator.text", element.text.toString(), "*${element.text}")

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val star = KtPsiFactory(actionContext.project).createStar()
        element.parent.addBefore(star, element)
    }
}