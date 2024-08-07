// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory

class ChangeObjectToClassFix(element: KtObjectDeclaration) : PsiUpdateModCommandAction<KtObjectDeclaration>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.change.object.to.class")

    override fun invoke(
        actionContext: ActionContext,
        element: KtObjectDeclaration,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(actionContext.project)
        element.getObjectKeyword()?.replace(psiFactory.createClassKeyword())
    }
}
