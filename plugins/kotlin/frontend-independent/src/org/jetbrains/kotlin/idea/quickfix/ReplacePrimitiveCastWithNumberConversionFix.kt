// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class ReplacePrimitiveCastWithNumberConversionFix(
    element: KtBinaryExpressionWithTypeRHS,
    private val targetShortType: String,
) : PsiUpdateModCommandAction<KtBinaryExpressionWithTypeRHS>(element) {

    override fun getPresentation(
        context: ActionContext,
        element: KtBinaryExpressionWithTypeRHS,
    ): Presentation = Presentation.of(
        KotlinBundle.message("replace.cast.with.call.to.to.0", targetShortType),
    )

    override fun getFamilyName(): String =
        KotlinBundle.message("replace.cast.with.primitive.conversion.method")

    override fun invoke(
        actionContext: ActionContext,
        element: KtBinaryExpressionWithTypeRHS,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(actionContext.project)
        val replaced = element.replaced(psiFactory.createExpressionByPattern("$0.to$1()", element.left, targetShortType))
        updater.moveCaretTo(replaced.endOffset)
    }
}
