// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange

class RemoveFunctionBodyFix(element: KtFunction) : PsiUpdateModCommandAction<KtFunction>(element) {
    override fun getFamilyName() = KotlinBundle.message("remove.function.body")

    override fun invoke(
        actionContext: ActionContext,
        element: KtFunction,
        updater: ModPsiUpdater,
    ) {
        val bodyExpression = element.bodyExpression ?: return
        val equalsToken = element.equalsToken
        if (equalsToken != null) {
            val commentSaver = CommentSaver(PsiChildRange(equalsToken.nextSibling, bodyExpression.prevSibling), true)
            element.deleteChildRange(equalsToken, bodyExpression)
            commentSaver.restore(element)
        } else {
            bodyExpression.delete()
        }
    }
}
