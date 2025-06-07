// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression

class RemoveUselessIsCheckFixForWhen(
    element: KtWhenConditionIsPattern,
    val compileTimeCheckResult: Boolean? = null,
) : PsiUpdateModCommandAction<KtWhenConditionIsPattern>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.useless.is.check")

    override fun invoke(
        context: ActionContext,
        element: KtWhenConditionIsPattern,
        updater: ModPsiUpdater,
    ) {
        val whenEntry = element.parent as? KtWhenEntry ?: return
        if (whenEntry.guard != null) return
        val whenExpression = whenEntry.parent as? KtWhenExpression ?: return

        if (compileTimeCheckResult?.not() ?: element.isNegated) {
            element.parent.delete()
        } else {
            whenExpression.entries.dropWhile { it != whenEntry }.forEach { it.delete() }
            val whenEntryExpression = whenEntry.expression ?: return
            val newEntry = KtPsiFactory(context.project).createWhenEntry("else -> ${whenEntryExpression.text}")
            whenExpression.addBefore(newEntry, whenExpression.closeBrace)
        }
    }
}
