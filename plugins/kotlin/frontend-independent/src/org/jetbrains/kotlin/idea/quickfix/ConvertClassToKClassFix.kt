// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

class ConvertClassToKClassFix(
    element: KtDotQualifiedExpression,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtDotQualifiedExpression>(element) {

    override fun getActionPresentation(
        context: ActionContext,
        element: KtDotQualifiedExpression,
    ): Presentation {
        val actionName = element.let { KotlinBundle.message("remove.0", it.lastChild?.text.toString()) }
        return Presentation.of(actionName).withPriority(PriorityAction.Priority.HIGH)
    }

    override fun getFamilyName(): String = KotlinBundle.message("remove.conversion.from.kclass.to.class")

    override fun invoke(
        context: ActionContext,
        element: KtDotQualifiedExpression,
        updater: ModPsiUpdater,
    ) {
        element.replace(element.firstChild)
    }
}
