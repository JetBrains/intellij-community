// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.EditCommaSeparatedListHelper
import org.jetbrains.kotlin.psi.KtWhenCondition
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class RemoveWhenConditionFix(
    element: KtWhenCondition,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtWhenCondition, Unit>(element, Unit) {

    override fun getFamilyName() = KotlinBundle.message("remove.condition")

    override fun invoke(
        actionContext: ActionContext,
        element: KtWhenCondition,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        element.let { EditCommaSeparatedListHelper.removeItem(it) }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            if (diagnostic.factory != Errors.SENSELESS_NULL_IN_WHEN) return null
            val whenCondition = diagnostic.psiElement.getStrictParentOfType<KtWhenCondition>() ?: return null
            val conditions = whenCondition.parent.safeAs<KtWhenEntry>()?.conditions?.size ?: return null
            if (conditions < 2) return null

            return RemoveWhenConditionFix(whenCondition).asIntention()
        }
    }
}
