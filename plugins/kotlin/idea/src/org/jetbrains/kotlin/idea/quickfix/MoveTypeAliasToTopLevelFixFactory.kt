// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtTypeAlias

internal object MoveTypeAliasToTopLevelFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        return (diagnostic.psiElement as? KtTypeAlias)
            ?.let { MoveTypeAliasToTopLevelFix(it) }
            ?.asIntention()
    }
}
