// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.EditCommaSeparatedListHelper
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtWhenCondition
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveWhenConditionFix private constructor(element: KtWhenCondition) : PsiUpdateModCommandAction<KtWhenCondition>(element) {

    override fun getFamilyName() = KotlinBundle.message("remove.condition")

    override fun invoke(
        actionContext: ActionContext,
        element: KtWhenCondition,
        updater: ModPsiUpdater,
    ) {
        element.let { EditCommaSeparatedListHelper.removeItem(it) }
    }

    companion object {
        fun createIfApplicable(element: KtElement): ModCommandAction? {
            val whenCondition = element.getStrictParentOfType<KtWhenCondition>() ?: return null
            val conditions = (whenCondition.parent as? KtWhenEntry)?.conditions?.size ?: return null
            if (conditions < 2) return null

            return RemoveWhenConditionFix(whenCondition)
        }
    }
}
