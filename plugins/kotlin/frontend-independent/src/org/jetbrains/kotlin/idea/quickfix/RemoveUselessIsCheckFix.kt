// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isNull

class RemoveUselessIsCheckFix(
    element: KtIsExpression,
    val result: Boolean? = null,
) : PsiUpdateModCommandAction<KtIsExpression>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.useless.is.check")

    override fun invoke(
        context: ActionContext,
        element: KtIsExpression,
        updater: ModPsiUpdater,
    ) {
        element.run {
            val expressionsText = result?.toString() ?: if (leftHandSide.isNull()) isNegated.toString() else isNegated.not().toString()
            val newExpression = KtPsiFactory(project).createExpression(expressionsText)
            replace(newExpression)
        }
    }
}
