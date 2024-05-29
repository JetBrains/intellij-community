// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeElement

class ChangeToStarProjectionFix(
    element: KtTypeElement,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtTypeElement, Unit>(element, Unit) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeElement,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val star = KtPsiFactory(actionContext.project).createStar()
        element.typeArgumentsAsTypes.forEach { it?.replace(star) }
    }

    override fun getActionName(
        actionContext: ActionContext,
        element: KtTypeElement,
        elementContext: Unit,
    ): String {
        val type = element.typeArgumentsAsTypes.joinToString { "*" }
        return KotlinBundle.message("fix.change.to.star.projection.text", "<$type>")
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.change.to.star.projection.family")
}
