// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object MissingConstructorKeywordFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        return (diagnostic.psiElement.getNonStrictParentOfType<KtPrimaryConstructor>())
            ?.let { MissingConstructorKeywordFix(it) }
            ?.asIntention()
    }
}