// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ReplaceIsCheckWithNullCheckFix(
    element: KtIsExpression,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtIsExpression>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.is.check.with.null.check")

    override fun invoke(
        context: ActionContext,
        element: KtIsExpression,
        updater: ModPsiUpdater,
    ) {
        val operator = if (element.isNegated) "==" else "!="
        val newExpression = KtPsiFactory(context.project).createExpressionByPattern("$0 $operator null", element.leftHandSide)
        element.replace(newExpression)
    }
}
