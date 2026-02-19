// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal object AddJvmStaticAnnotationFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val nameReference = diagnostic.psiElement as? KtNameReferenceExpression ?: return null
        return AddJvmStaticAnnotationFix.createIfApplicable(nameReference)?.asIntention()
    }
}
