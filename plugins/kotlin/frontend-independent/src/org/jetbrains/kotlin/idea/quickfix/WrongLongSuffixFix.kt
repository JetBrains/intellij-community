// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

class WrongLongSuffixFix(element: KtConstantExpression) :
    KotlinPsiUpdateModCommandAction.ElementContextless<KtConstantExpression>(element) {
    private val corrected = element.text.trimEnd('l') + 'L'

    override fun getActionPresentation(
        context: ActionContext,
        element: KtConstantExpression,
    ): Presentation {
        return Presentation.of(KotlinBundle.message("change.to.0", corrected))
    }

    override fun getFamilyName(): String = KotlinBundle.message("change.to.correct.long.suffix.l")

    override fun invoke(
        context: ActionContext,
        element: KtConstantExpression,
        updater: ModPsiUpdater,
    ) {
        element.replace(KtPsiFactory(context.project).createExpression(corrected))
    }
}
