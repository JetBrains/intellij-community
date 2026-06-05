// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeElement

class ChangeToStarProjectionFix(element: KtTypeElement) : KotlinPsiUpdateModCommandAction.ElementContextless<KtTypeElement>(element) {

    override fun invoke(
        context: ActionContext,
        element: KtTypeElement,
        updater: ModPsiUpdater,
    ) {
        val star = KtPsiFactory(context.project).createStar()
        element.typeArgumentsAsTypes.forEach { it?.replace(star) }
    }

    override fun getActionPresentation(
        context: ActionContext,
        element: KtTypeElement,
    ): Presentation {
        val type = element.typeArgumentsAsTypes.joinToString { "*" }
        return Presentation.of(KotlinBundle.message("fix.change.to.star.projection.text", "<$type>"))
    }

    override fun getFamilyName(): String =
        KotlinBundle.message("fix.change.to.star.projection.family")
}
