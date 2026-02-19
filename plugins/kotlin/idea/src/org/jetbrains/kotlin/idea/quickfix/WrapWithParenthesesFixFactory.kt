// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtBinaryExpression

internal object WrapWithParenthesesFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? =
        diagnostic.psiElement.parentOfType<KtBinaryExpression>()
            ?.takeIf { it.operationReference.text == "suspend" }
            ?.right
            ?.let(::WrapWithParenthesesFix)
            ?.asIntention()
}
