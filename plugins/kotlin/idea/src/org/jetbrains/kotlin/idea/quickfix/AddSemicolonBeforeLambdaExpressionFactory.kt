// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

object AddSemicolonBeforeLambdaExpressionFactory: KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? =
        diagnostic.psiElement.getNonStrictParentOfType<KtLambdaExpression>()?.let { AddSemicolonBeforeLambdaExpressionFix(it).asIntention() }
}
