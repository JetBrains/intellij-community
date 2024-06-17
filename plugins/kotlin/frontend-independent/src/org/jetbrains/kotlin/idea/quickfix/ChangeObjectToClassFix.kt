// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory

class ChangeObjectToClassFix(
    element: KtObjectDeclaration,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtObjectDeclaration, Unit>(element, Unit) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.change.object.to.class")

    override fun invoke(
        actionContext: ActionContext,
        element: KtObjectDeclaration,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(actionContext.project)
        element.getObjectKeyword()?.replace(psiFactory.createClassKeyword())
    }
}
