// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory

class ChangeToFunctionInvocationFix(element: KtExpression) : PsiUpdateModCommandAction<KtExpression>(element) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(actionContext.project)
        val nextLiteralStringEntry = element.parent.nextSibling as? KtLiteralStringTemplateEntry
        val nextText = nextLiteralStringEntry?.text
        if (nextText != null && nextText.startsWith("(") && nextText.contains(")")) {
            val parentheses = nextText.takeWhile { it != ')' } + ")"
            val newNextText = nextText.removePrefix(parentheses)
            if (newNextText.isNotEmpty()) {
                nextLiteralStringEntry.replace(psiFactory.createLiteralStringTemplateEntry(newNextText))
            } else {
                nextLiteralStringEntry.delete()
            }
            element.replace(psiFactory.createExpression("${element.text}$parentheses"))
        } else {
            element.replace(psiFactory.createExpression("${element.text}()"))
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.change.to.function.invocation")
}
