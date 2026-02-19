// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters1

internal object ConvertCollectionLiteralToIntArrayOfFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val unsupported = (diagnostic as? DiagnosticWithParameters1<*, *>)?.a as? String ?: return null

        return ConvertCollectionLiteralToIntArrayOfFix.createIfApplicable(
            element = diagnostic.psiElement,
            unsupportedFeature = unsupported
        )?.asIntention()
    }
}
