// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters2
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConstantExpressionValue
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyExpressionFix
import org.jetbrains.kotlin.psi.KtExpression

internal object SimplifyComparisonFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val expression = diagnostic.psiElement as? KtExpression ?: return null
        val value = (diagnostic as? DiagnosticWithParameters2<*, *, *>)?.b as? Boolean ?: return null
        return SimplifyExpressionFix(
            expression,
            constantExpressionValue = ConstantExpressionValue.of(value),
            familyName = KotlinBundle.message("simplify.comparison")
        ).asIntention()
    }
}