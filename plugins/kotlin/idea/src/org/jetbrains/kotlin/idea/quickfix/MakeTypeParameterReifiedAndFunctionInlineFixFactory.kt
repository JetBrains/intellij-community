// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object MakeTypeParameterReifiedAndFunctionInlineFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = Errors.CANNOT_CHECK_FOR_ERASED.cast(diagnostic)
        val typeReference = element.psiElement as? KtTypeReference ?: return null
        val function = typeReference.getStrictParentOfType<KtNamedFunction>() ?: return null
        val typeParameter = function.typeParameterList?.parameters?.firstOrNull {
            it.descriptor == element.a.constructor.declarationDescriptor
        } ?: return null
        return MakeTypeParameterReifiedAndFunctionInlineFix(typeParameter).asIntention()
    }
}
