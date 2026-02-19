// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.containingClass

class AssignToPropertyFix(
    element: KtNameReferenceExpression,
    private val hasSingleImplicitReceiver: Boolean,
) : PsiUpdateModCommandAction<KtNameReferenceExpression>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.assign.to.property")

    override fun invoke(
        context: ActionContext,
        element: KtNameReferenceExpression,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(context.project)
        if (hasSingleImplicitReceiver) {
            element.replace(psiFactory.createExpressionByPattern("this.$0", element))
        } else {
            element.containingClass()?.name?.let {
                element.replace(psiFactory.createExpressionByPattern("this@$0.$1", it, element))
            }
        }
    }
}
