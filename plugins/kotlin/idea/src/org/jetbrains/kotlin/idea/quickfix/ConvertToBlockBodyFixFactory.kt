// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.intentions.ConvertToBlockBodyIntention
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object ConvertToBlockBodyFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = Errors.RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY.cast(diagnostic).psiElement
        val declaration = element.getStrictParentOfType<KtDeclarationWithBody>() ?: return null
        val context = ConvertToBlockBodyIntention.Holder.createContext(declaration) ?: return null
        return ConvertToBlockBodyFix(declaration, context).asIntention()
    }
}
