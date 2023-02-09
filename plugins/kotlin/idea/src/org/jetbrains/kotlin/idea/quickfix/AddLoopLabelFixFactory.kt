// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddLoopLabelFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object AddLoopLabelFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = diagnostic.psiElement as? KtExpressionWithLabel
        assert(element is KtBreakExpression || element is KtContinueExpression)
        assert((element as? KtLabeledExpression)?.getLabelName() == null)
        val loop = element?.getStrictParentOfType<KtLoopExpression>() ?: return null
        return AddLoopLabelFix(loop, element)
    }
}
