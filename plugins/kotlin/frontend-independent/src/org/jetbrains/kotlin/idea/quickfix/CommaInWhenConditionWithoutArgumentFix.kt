/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class CommaInWhenConditionWithoutArgumentFix(
    element: KtWhenExpression,
) : PsiUpdateModCommandAction<KtWhenExpression>(element), CleanupFix.ModCommand {

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.in.when")

    override fun invoke(
        context: ActionContext,
        element: KtWhenExpression,
        updater: ModPsiUpdater,
    ) {
        replaceCommasWithOrsInWhenExpression(element)
    }

    private class WhenEntryConditionsData(
        val conditions: List<KtExpression>,
        val first: PsiElement,
        val last: PsiElement,
        val arrow: PsiElement
    )

    private fun replaceCommasWithOrsInWhenExpression(whenExpression: KtWhenExpression) {
        for (whenEntry in whenExpression.entries) {
            if (whenEntry.conditions.size > 1) {
                val conditionsData = getConditionsDataOrNull(whenEntry) ?: return
                // Leave branch untouched if there are no valid conditions
                if (conditionsData.conditions.isEmpty()) continue
                val replacement = KtPsiFactory(whenEntry.project).buildExpression {
                    appendExpressions(conditionsData.conditions, separator = "||")
                }
                whenEntry.deleteChildRange(conditionsData.first, conditionsData.last)
                whenEntry.addBefore(replacement, conditionsData.arrow)
            }
        }
    }

    private fun getConditionsDataOrNull(whenEntry: KtWhenEntry): WhenEntryConditionsData? {
        val conditions = mutableListOf<KtExpression>()

        var arrow: PsiElement? = null

        var child = whenEntry.firstChild
        whenEntryChildren@ while (child != null) {
            when {
                child is KtWhenConditionWithExpression -> {
                    conditions.addIfNotNull(child.expression)
                }
                child.node.elementType == KtTokens.ARROW -> {
                    arrow = child
                    break@whenEntryChildren
                }
            }
            child = child.nextSibling
        }

        val last = child?.prevSibling

        return if (arrow != null && last != null)
            WhenEntryConditionsData(conditions, whenEntry.firstChild, last, arrow)
        else
            null
    }
}