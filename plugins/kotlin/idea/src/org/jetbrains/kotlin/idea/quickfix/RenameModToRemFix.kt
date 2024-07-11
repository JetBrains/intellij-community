// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.types.expressions.OperatorConventions.REM_TO_MOD_OPERATION_NAMES

internal object RenameModToRemFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        if (diagnostic.factory != Errors.DEPRECATED_BINARY_MOD && diagnostic.factory != Errors.FORBIDDEN_BINARY_MOD) return null

        val operatorMod = diagnostic.psiElement.getNonStrictParentOfType<KtNamedFunction>() ?: return null
        val newName = operatorMod.nameAsName?.let { REM_TO_MOD_OPERATION_NAMES.inverse()[it] } ?: return null
        return RenameModToRemFix(operatorMod, newName)
    }
}