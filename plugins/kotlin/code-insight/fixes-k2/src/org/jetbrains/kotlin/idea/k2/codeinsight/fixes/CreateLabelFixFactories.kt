// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.CreateLabelFix
import org.jetbrains.kotlin.idea.quickfix.CreateLabelFix.Companion.getContainingLambdas
import org.jetbrains.kotlin.idea.quickfix.CreateLabelFix.Companion.getContainingLoops
import org.jetbrains.kotlin.psi.*

internal object CreateLabelFixFactories {

    val unresolvedLabelFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnresolvedLabel ->
        val returnExpression = diagnostic.psi as? KtReturnExpression ?: return@IntentionBased emptyList()
        val labelReferenceExpression = returnExpression.getTargetLabel() as? KtLabelReferenceExpression ?: return@IntentionBased emptyList()

        val fixes = if (labelReferenceExpression.getContainingLambdas().any()) {
            listOf(CreateLabelFix.ForLambda(labelReferenceExpression))
        } else {
            emptyList()
        }

        return@IntentionBased fixes
    }

    val notALoopLabelFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NotALoopLabel ->
        val breakOrContinueExpression =
            diagnostic.psi as? KtBreakExpression ?: diagnostic.psi as? KtContinueExpression ?: return@IntentionBased emptyList()

        val labelReferenceExpression =
            breakOrContinueExpression.getTargetLabel() as? KtLabelReferenceExpression ?: return@IntentionBased emptyList()

        val fixes = if (labelReferenceExpression.getContainingLoops().any()) {
            listOf(CreateLabelFix.ForLoop(labelReferenceExpression))
        } else {
            emptyList()
        }

        return@IntentionBased fixes
    }
}

