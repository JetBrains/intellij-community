// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinModCommandAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class AddToStringFix(
    element: KtExpression,
    elementContext: ElementContext,
) : KotlinModCommandAction.ElementBased<KtExpression, AddToStringFix.ElementContext>(element, elementContext),
    LowPriorityAction {

    data class ElementContext(
        val useSafeCallOperator: Boolean,
    )

    constructor(
        element: KtExpression,
        useSafeCallOperator: Boolean,
    ) : this(element, ElementContext(useSafeCallOperator))

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.tostring.call.family")

    override fun getActionName(
        context: ActionContext,
        element: KtExpression,
        elementContext: ElementContext,
    ): String = KotlinBundle.message(
        if (elementContext.useSafeCallOperator) "fix.add.tostring.call.text.safe" else "fix.add.tostring.call.text",
    )

    override fun invoke(
        context: ActionContext,
        element: KtExpression,
        elementContext: ElementContext,
        updater: ModPsiUpdater,
    ) {
        val operator = if (elementContext.useSafeCallOperator) "?" else ""
        val pattern = "$0${operator}.toString()"

        val expressionToInsert = KtPsiFactory(element.project).createExpressionByPattern(pattern, element)
        val newExpression = element.replaced(expressionToInsert)
        updater.moveCaretTo(newExpression.endOffset)
    }
}