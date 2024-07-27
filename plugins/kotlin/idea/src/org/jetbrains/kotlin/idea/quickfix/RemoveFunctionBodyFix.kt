// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class RemoveFunctionBodyFix(
    element: KtFunction,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtFunction, Unit>(element, Unit) {
    override fun getFamilyName() = KotlinBundle.message("remove.function.body")

    override fun invoke(
        actionContext: ActionContext,
        element: KtFunction,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val bodyExpression = element.bodyExpression!!
        val equalsToken = element.equalsToken
        if (equalsToken != null) {
            val commentSaver = CommentSaver(PsiChildRange(equalsToken.nextSibling, bodyExpression.prevSibling), true)
            element.deleteChildRange(equalsToken, bodyExpression)
            commentSaver.restore(element)
        } else {
            bodyExpression.delete()
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val function = diagnostic.psiElement.getNonStrictParentOfType<KtFunction>() ?: return null
            if (!function.hasBody()) return null
            return RemoveFunctionBodyFix(function).asIntention()
        }
    }
}
