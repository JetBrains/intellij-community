// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction

object InlineClassConstructorNotValParameterFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val parameter = Errors.VALUE_CLASS_CONSTRUCTOR_NOT_FINAL_READ_ONLY_PARAMETER.cast(diagnostic).psiElement
        return if (parameter.isMutable)
            ChangeVariableMutabilityFix(parameter, false)
        else
            AddValVarToConstructorParameterAction.QuickFix(parameter)
    }
}
