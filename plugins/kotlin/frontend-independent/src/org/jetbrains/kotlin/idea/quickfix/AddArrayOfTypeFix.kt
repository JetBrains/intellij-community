// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class AddArrayOfTypeFix(
    element: KtExpression,
    private val prefix: String,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, Unit>(element, Unit) {

    override fun getActionName(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: Unit,
    ): String = KotlinBundle.message("fix.add.array.of.type.text", prefix)

    override fun getFamilyName() = KotlinBundle.message("fix.add.array.of.type.family")

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val arrayOfExpression = KtPsiFactory(actionContext.project).createExpressionByPattern("$0($1)", prefix, element)
        element.replace(arrayOfExpression)
    }
}
