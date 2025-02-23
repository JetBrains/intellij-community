// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi

/*sealed*/ fun interface KotlinQuickFixFactory<DIAGNOSTIC : KaDiagnosticWithPsi<*>> {

    fun KaSession.createQuickFixes(diagnostic: DIAGNOSTIC): List<CommonIntentionAction>

    /**
     * Creates [IntentionAction]s from a diagnostic.
     */
    fun interface IntentionBased<DIAGNOSTIC : KaDiagnosticWithPsi<*>> : KotlinQuickFixFactory<DIAGNOSTIC> {

        override fun KaSession.createQuickFixes(diagnostic: DIAGNOSTIC): List<IntentionAction>
    }

    /**
     * Creates [ModCommandAction]s from a diagnostic.
     */
    fun interface ModCommandBased<DIAGNOSTIC : KaDiagnosticWithPsi<*>> : KotlinQuickFixFactory<DIAGNOSTIC> {

        override fun KaSession.createQuickFixes(diagnostic: DIAGNOSTIC): List<ModCommandAction>
    }
}
