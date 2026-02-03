// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object AddReifiedToTypeParameterOfFunctionFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = Errors.TYPE_PARAMETER_AS_REIFIED.cast(diagnostic)
        val function = element.psiElement.getStrictParentOfType<KtNamedFunction>()
        val parameter = function?.typeParameterList?.parameters?.getOrNull(element.a.index) ?: return null
        return AddReifiedToTypeParameterOfFunctionFix(parameter, function).asIntention()
    }
}