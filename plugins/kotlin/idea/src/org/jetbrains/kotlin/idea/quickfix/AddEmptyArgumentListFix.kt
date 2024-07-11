// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createValueArgumentListByPattern
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddEmptyArgumentListFix(
    element: KtCallExpression
) : KotlinPsiUpdateModCommandAction.ElementBased<KtCallExpression, Unit>(element, Unit) {
    override fun getFamilyName(): String = KotlinBundle.message("add.empty.argument.list")

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val calleeExpression = element.calleeExpression ?: return
        val emptyArgumentList = KtPsiFactory(actionContext.project).createValueArgumentListByPattern("()")
        element.addAfter(emptyArgumentList, calleeExpression)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic) =
            diagnostic.psiElement.parent.safeAs<KtCallExpression>()?.let { AddEmptyArgumentListFix(it) }?.asIntention()
    }
}