// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.quickfix.CreateLabelFix.Companion.getContainingLambdas
import org.jetbrains.kotlin.idea.quickfix.CreateLabelFix.Companion.getContainingLoops
import org.jetbrains.kotlin.psi.*

internal object CreateLabelFixFactory : KotlinSingleIntentionActionFactory() {

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val labelReferenceExpression = diagnostic.psiElement as? KtLabelReferenceExpression ?: return null
        return when ((labelReferenceExpression.parent as? KtContainerNode)?.parent) {
            is KtBreakExpression, is KtContinueExpression -> {
                if (labelReferenceExpression.getContainingLoops().any()) {
                    CreateLabelFix.ForLoop(labelReferenceExpression)
                } else {
                    null
                }
            }

            is KtReturnExpression -> {
                if (labelReferenceExpression.getContainingLambdas().any()) {
                    CreateLabelFix.ForLambda(labelReferenceExpression)
                } else {
                    null
                }
            }

            else -> null
        }
    }
}