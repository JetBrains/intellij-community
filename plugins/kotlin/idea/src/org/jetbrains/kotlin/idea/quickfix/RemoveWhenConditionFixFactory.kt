// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtElement

internal object RemoveWhenConditionFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        if (diagnostic.factory != Errors.SENSELESS_NULL_IN_WHEN) return null
        val element = diagnostic.psiElement as? KtElement ?: return null
        return RemoveWhenConditionFix.createIfApplicable(element)?.asIntention()
    }
}
