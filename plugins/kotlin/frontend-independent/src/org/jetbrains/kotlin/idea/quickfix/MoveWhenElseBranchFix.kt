// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression

class MoveWhenElseBranchFix private constructor(element: KtWhenExpression) : PsiUpdateModCommandAction<KtWhenExpression>(element) {
    override fun getFamilyName() = KotlinBundle.message("move.else.branch.to.the.end")

    override fun invoke(
        actionContext: ActionContext,
        element: KtWhenExpression,
        updater: ModPsiUpdater,
    ) {
        val entries = element.entries
        val lastEntry = entries.lastOrNull() ?: return
        val elseEntry = entries.singleOrNull { it.isElse } ?: return
        val cursorOffset = actionContext.offset - elseEntry.textOffset

        val insertedBranch = element.addAfter(elseEntry, lastEntry) as KtWhenEntry
        elseEntry.delete()

        updater.moveCaretTo(insertedBranch.startOffset + cursorOffset)
    }

    companion object {
        fun createIfApplicable(element: KtWhenExpression): MoveWhenElseBranchFix? {
            return if (KtPsiUtil.checkWhenExpressionHasSingleElse(element))
                MoveWhenElseBranchFix(element)
            else null
        }
    }
}
