// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.intentions.getCallableDescriptor
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject

internal object ChangeToPropertyAccessFix : KotlinSingleIntentionActionFactory() {

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val expression = UnresolvedInvocationQuickFix.findAcceptableParentCallExpression(diagnostic.psiElement)
            ?: return null

        val isObjectCall = expression.calleeExpression?.getCallableDescriptor() is FakeCallableDescriptorForObject

        val quickFix = if (isObjectCall)
            UnresolvedInvocationQuickFix.RemoveInvocationQuickFix(expression)
        else
            UnresolvedInvocationQuickFix.ChangeToPropertyAccessQuickFix(expression)

        return quickFix.asIntention()
    }
}