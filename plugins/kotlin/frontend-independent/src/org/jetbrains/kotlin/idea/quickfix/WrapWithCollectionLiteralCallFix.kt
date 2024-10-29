// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class WrapWithCollectionLiteralCallFix(
    element: KtExpression,
    private val functionName: String,
    private val wrapInitialElement: Boolean
) : PsiUpdateModCommandAction<KtExpression>(element)  {
    override fun getFamilyName(): String = KotlinBundle.message("wrap.with.collection.literal.call")

    override fun getPresentation(context: ActionContext, element: KtExpression): Presentation {
        val text = if (wrapInitialElement) {
            KotlinBundle.message("wrap.element.with.0.call", functionName)
        } else {
            KotlinBundle.message("replace.with.0.call", functionName)
        }
        return Presentation.of(text)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(element.project)

        val replaced =
            if (wrapInitialElement)
                element.replaced(psiFactory.createExpressionByPattern("$functionName($0)", element))
            else
                element.replaced(psiFactory.createExpression("$functionName()"))

        updater.moveCaretTo(replaced.endOffset)
    }
}