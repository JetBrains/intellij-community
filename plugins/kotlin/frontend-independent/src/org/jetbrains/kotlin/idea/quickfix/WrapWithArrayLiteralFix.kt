// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class WrapWithArrayLiteralFix(element: KtExpression) : KotlinPsiUpdateModCommandAction.ElementContextless<KtExpression>(element) {

    override fun getFamilyName(): String =
        KotlinBundle.message("wrap.with.array.literal")

    override fun getActionPresentation(
        context: ActionContext,
        element: KtExpression,
    ): Presentation = Presentation.of(
        KotlinBundle.message("wrap.with"),
    )

    override fun invoke(
        context: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater,
    ) {
        element.replace(KtPsiFactory(context.project).createExpressionByPattern("[$0]", element))
    }
}