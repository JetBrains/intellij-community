// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

class AddReturnToLastExpressionInFunctionFix(
    element: KtNamedFunction,
) : PsiUpdateModCommandAction<KtDeclarationWithBody>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.return.last.expression")

    override fun invoke(
        actionContext: ActionContext,
        element: KtDeclarationWithBody,
        updater: ModPsiUpdater,
    ) {
        val element = element as? KtNamedFunction ?: return
        val last = element.bodyBlockExpression?.statements?.lastOrNull() ?: return
        last.replace(KtPsiFactory(actionContext.project).createExpression("return ${last.text}"))
    }
}
