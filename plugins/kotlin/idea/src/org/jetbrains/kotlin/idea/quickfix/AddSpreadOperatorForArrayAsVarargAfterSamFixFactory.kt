// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors

internal object AddSpreadOperatorForArrayAsVarargAfterSamFixFactory : KotlinSingleIntentionActionFactory() {
    public override fun createAction(diagnostic: Diagnostic): IntentionAction {
        val diagnosticWithParameters = Errors.TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG.cast(diagnostic)
        val argument = diagnosticWithParameters.psiElement

        return AddSpreadOperatorForArrayAsVarargAfterSamFix(argument).asIntention()
    }
}
