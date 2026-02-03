// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.migration

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.MigrateTypeParameterListFix
import org.jetbrains.kotlin.psi.KtTypeParameterList

internal object MigrateTypeParameterListFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val typeParameterList = diagnostic.psiElement as? KtTypeParameterList ?: return null
        return MigrateTypeParameterListFix(typeParameterList).asIntention()
    }
}
