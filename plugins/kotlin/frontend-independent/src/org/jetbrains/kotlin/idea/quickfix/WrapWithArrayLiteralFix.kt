// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class WrapWithArrayLiteralFix(element: KtExpression) : PsiUpdateModCommandAction<KtExpression>(element) {

    override fun getFamilyName(): String =
        KotlinBundle.message("wrap.with.array.literal")

    override fun getPresentation(
        context: ActionContext,
        element: KtExpression,
    ): Presentation = Presentation.of(
        KotlinBundle.message("wrap.with"),
    )

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater,
    ) {
        element.replace(KtPsiFactory(actionContext.project).createExpressionByPattern("[$0]", element))
    }
}