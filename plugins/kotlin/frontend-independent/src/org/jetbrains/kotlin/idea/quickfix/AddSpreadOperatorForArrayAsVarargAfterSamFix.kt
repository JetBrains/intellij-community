// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory

class AddSpreadOperatorForArrayAsVarargAfterSamFix(
    element: PsiElement,
) : PsiUpdateModCommandAction<PsiElement>(element) {
    override fun getFamilyName(): String = KotlinBundle.message("fix.add.spread.operator.after.sam")

    override fun invoke(
        context: ActionContext,
        element: PsiElement,
        updater: ModPsiUpdater,
    ) {
        element.addBefore(KtPsiFactory(context.project).createStar(), element.firstChild)
    }
}
