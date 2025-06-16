// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory

class AddReturnToUnusedLastExpressionInFunctionFix(element: KtElement) : PsiUpdateModCommandAction<KtElement>(element) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.return.before.expression")

    override fun invoke(
        context: ActionContext,
        element: KtElement,
        updater: ModPsiUpdater,
    ) {
        element.replace(KtPsiFactory(context.project).createExpression("return ${element.text}"))
    }
}
