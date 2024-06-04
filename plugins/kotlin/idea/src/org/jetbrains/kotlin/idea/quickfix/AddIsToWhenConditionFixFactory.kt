// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression

internal object AddIsToWhenConditionFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtWhenConditionWithExpression>? {
        val element = diagnostic.psiElement.parent as? KtWhenConditionWithExpression ?: return null
        return AddIsToWhenConditionFix(element, element.text)
    }
}