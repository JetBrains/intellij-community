// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddDependencyQuickFixHelper
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

object KotlinAddOrderEntryActionFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val simpleExpression = diagnostic.psiElement as? KtSimpleNameExpression ?: return emptyList()
        return AddDependencyQuickFixHelper.createQuickFix(simpleExpression)
    }
}
