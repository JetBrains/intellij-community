// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS

internal object MakeConstructorParameterPropertyFixFactory : KotlinIntentionActionsFactory() {

    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val ktReference = Errors.UNRESOLVED_REFERENCE.cast(diagnostic).a as? KtNameReferenceExpression ?: return emptyList()

        val valOrVar = if (ktReference.getAssignmentByLHS() != null) KotlinValVar.Var else KotlinValVar.Val
        val ktParameter = ktReference.getPrimaryConstructorParameterWithSameName() ?: return emptyList()
        if (ktParameter.hasValOrVar()) return emptyList()
        val containingClass = ktParameter.containingClass()!!
        val className = if (containingClass != ktReference.containingClass()) containingClass.nameAsSafeName.asString() else null

        return listOf(
            MakeConstructorParameterPropertyFix(ktParameter, valOrVar, className).asIntention()
        )
    }
}
