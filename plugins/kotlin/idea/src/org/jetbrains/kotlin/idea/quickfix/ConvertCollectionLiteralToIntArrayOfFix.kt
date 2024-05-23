// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters1
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression

internal object ConvertCollectionLiteralToIntArrayOfFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        return diagnostic
            .takeIf { "Collection literals outside of annotations" == (it as? DiagnosticWithParameters1<*, *>)?.a }
            ?.psiElement
            ?.let { it as? KtCollectionLiteralExpression }
            ?.let(::ConvertCollectionLiteralToIntArrayOfFix)
            ?.let(ModCommandAction::asIntention)
    }
}
