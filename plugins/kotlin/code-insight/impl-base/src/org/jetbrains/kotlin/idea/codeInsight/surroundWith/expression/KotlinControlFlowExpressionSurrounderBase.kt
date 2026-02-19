// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurrounder
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

abstract class KotlinControlFlowExpressionSurrounderBase : KotlinExpressionSurrounder() {
    override val isApplicableToStatements: Boolean
        get() = false

    override fun surroundExpression(context: ActionContext, expression: KtExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(context.project)

        val newElement = psiFactory.createExpressionByPattern(getPattern().replace("$0", expression.text))
        val replaced = expression.replaced(newElement)

        getRange(context, replaced, updater)?.let {
            updater.select(it)
        }
    }

    protected abstract fun getPattern(exceptionClasses: List<ClassId> = emptyList()): String
    protected abstract fun getRange(context: ActionContext, replaced: KtExpression, updater: ModPsiUpdater): TextRange?
}