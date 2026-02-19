// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal object AddReturnExpressionFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = Errors.NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY.cast(diagnostic).psiElement
        val function = element.safeAs<KtNamedFunction>() ?: return null
        return AddReturnExpressionFix(function).asIntention()
    }
}
