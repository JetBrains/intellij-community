// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression

sealed class UnresolvedInvocationQuickFix(element: KtCallExpression) : PsiUpdateModCommandAction<KtCallExpression>(element) {

    companion object {

        fun findAcceptableCallExpression(element: PsiElement): KtCallExpression? =
            (element as? KtCallExpression)
            ?.takeIf { it.valueArguments.isEmpty() }

        fun findAcceptableParentCallExpression(element: PsiElement): KtCallExpression? =
            findAcceptableCallExpression(element.parent)
    }

    class ChangeToPropertyAccessQuickFix(expression: KtCallExpression) : UnresolvedInvocationQuickFix(expression) {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("fix.change.to.property.access.family.change")
    }

    class RemoveInvocationQuickFix(expression: KtCallExpression) : UnresolvedInvocationQuickFix(expression) {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("fix.change.to.property.access.family.remove")
    }

    final override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        updater: ModPsiUpdater,
    ) {
        element.replace(element.calleeExpression as KtExpression)
    }
}
