// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.CreateLabelFix
import org.jetbrains.kotlin.idea.quickfix.CreateLabelFix.Companion.getContainingLambdas
import org.jetbrains.kotlin.idea.quickfix.CreateLabelFix.Companion.getContainingLoops
import org.jetbrains.kotlin.psi.*

internal object CreateLabelFixFactory {

    val createLabelFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnresolvedLabel ->

        val labelReferenceExpression = diagnostic.psi as? KtLabelReferenceExpression ?: return@IntentionBased emptyList()
        val fixes = when ((labelReferenceExpression.parent as? KtContainerNode)?.parent) {
            is KtBreakExpression, is KtContinueExpression -> {
                if (labelReferenceExpression.getContainingLoops().any()) {
                    listOf(CreateLabelFix.ForLoop(labelReferenceExpression))
                } else {
                    emptyList()
                }
            }

            is KtReturnExpression -> {
                if (labelReferenceExpression.getContainingLambdas().any()) {
                    listOf(CreateLabelFix.ForLambda(labelReferenceExpression))
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }
        return@IntentionBased fixes
    }
}

