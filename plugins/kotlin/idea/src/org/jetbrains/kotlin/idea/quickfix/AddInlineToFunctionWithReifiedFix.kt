// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object AddInlineToFunctionWithReifiedFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = Errors.REIFIED_TYPE_PARAMETER_NO_INLINE.cast(diagnostic)
        val function = element.psiElement.getStrictParentOfType<KtNamedFunction>() ?: return null
        return AddModifierFixMpp(function, KtTokens.INLINE_KEYWORD).asIntention()
    }
}