// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtReturnExpression

class RemoveRedundantReturnFix(element: KtReturnExpression) : PsiUpdateModCommandAction<KtReturnExpression>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.redundant.return")

    override fun invoke(context: ActionContext, element: KtReturnExpression, updater: ModPsiUpdater) {
        val returnedExpression = element.returnedExpression
        if (returnedExpression != null) {
            element.replace(returnedExpression)
        } else {
            element.delete()
        }
    }
}