// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi

/*sealed*/ fun interface KotlinQuickFixFactory<DIAGNOSTIC : KtDiagnosticWithPsi<*>> {

    context(KtAnalysisSession)
    fun createQuickFixes(diagnostic: DIAGNOSTIC): List<CommonIntentionAction>

    /**
     * Creates [IntentionAction]s from a diagnostic.
     */
    fun interface IntentionBased<DIAGNOSTIC : KtDiagnosticWithPsi<*>> : KotlinQuickFixFactory<DIAGNOSTIC> {

        context(KtAnalysisSession)
        override fun createQuickFixes(diagnostic: DIAGNOSTIC): List<IntentionAction>
    }

    /**
     * Creates [ModCommandAction]s from a diagnostic.
     */
    fun interface ModCommandBased<DIAGNOSTIC : KtDiagnosticWithPsi<*>> : KotlinQuickFixFactory<DIAGNOSTIC> {

        context(KtAnalysisSession)
        override fun createQuickFixes(diagnostic: DIAGNOSTIC): List<ModCommandAction>
    }
}
