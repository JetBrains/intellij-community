// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object RemoveFunctionBodyFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val function = diagnostic.psiElement.getNonStrictParentOfType<KtFunction>() ?: return null
        if (!function.hasBody()) return null
        return RemoveFunctionBodyFix(function).asIntention()
    }
}
