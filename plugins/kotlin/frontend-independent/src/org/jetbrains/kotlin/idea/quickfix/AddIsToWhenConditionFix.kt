// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class AddIsToWhenConditionFix(
    element: KtWhenConditionWithExpression,
    private val referenceText: String
) : PsiUpdateModCommandAction<KtWhenConditionWithExpression>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.is.to.when", referenceText)

    override fun invoke(
        actionContext: ActionContext,
        element: KtWhenConditionWithExpression,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(actionContext.project)
        val replaced = element.replaced(psiFactory.createWhenCondition("is ${element.text}"))
        updater.moveCaretTo(replaced.endOffset)
    }
}
