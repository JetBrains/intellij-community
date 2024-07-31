// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern

class RemoveIsFromIsEnumEntryFix(element: KtWhenConditionIsPattern) : PsiUpdateModCommandAction<KtWhenConditionIsPattern>(element) {

    override fun getFamilyName() = KotlinBundle.message("remove.expression", "is")

    override fun invoke(
        actionContext: ActionContext,
        element: KtWhenConditionIsPattern,
        updater: ModPsiUpdater,
    ) {
        val typeReference = element.typeReference?.text ?: return
        element.replace(KtPsiFactory(actionContext.project).createWhenCondition(typeReference))
    }
}