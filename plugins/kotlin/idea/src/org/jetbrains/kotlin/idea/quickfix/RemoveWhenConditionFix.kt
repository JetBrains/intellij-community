// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtWhenCondition
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal object RemoveWhenConditionFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        if (diagnostic.factory != Errors.SENSELESS_NULL_IN_WHEN) return null
        val whenCondition = diagnostic.psiElement.getStrictParentOfType<KtWhenCondition>() ?: return null
        val conditions = whenCondition.parent.safeAs<KtWhenEntry>()?.conditions?.size ?: return null
        if (conditions < 2) return null

        return RemoveWhenConditionFix(whenCondition).asIntention()
    }
}
