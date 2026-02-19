// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

class TooLongCharLiteralToStringFix(element: KtConstantExpression) : PsiUpdateModCommandAction<KtConstantExpression>(element) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtConstantExpression,
        updater: ModPsiUpdater,
    ) {
        val text = element.text
        if (!(text.startsWith("'") && text.endsWith("'") && text.length >= 2)) {
            return
        }

        val newStringContent = text
            .slice(1..text.length - 2)
            .replace("\\\"", "\"")
            .replace("\"", "\\\"")
        val newElement = KtPsiFactory(actionContext.project).createStringTemplate(newStringContent)

        element.replace(newElement)
    }

    override fun getFamilyName(): String = KotlinBundle.message("convert.too.long.character.literal.to.string")

    companion object {
        fun createIfApplicable(element: KtConstantExpression): ModCommandAction? {
            if (element.text == "'\\'") return null
            if (element.text.startsWith("\"")) return null
            return TooLongCharLiteralToStringFix(element = element)
        }
    }
}
