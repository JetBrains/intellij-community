// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext

internal object InlineTypeParameterFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = Errors.FINAL_UPPER_BOUND.cast(diagnostic).psiElement
        val parameterListOwner = element.getStrictParentOfType<KtTypeParameterListOwner>() ?: return null
        val parameterList = parameterListOwner.typeParameterList ?: return null
        val (parameter, _, _) = prepareInlineTypeParameterContext(element, parameterList) ?: return null

        val context = parameterListOwner.analyzeWithContent()
        val parameterDescriptor = context[BindingContext.TYPE_PARAMETER, parameter] ?: return null

        val typeReferencesToInline = parameterListOwner
            .descendantsOfType<KtTypeReference>()
            .filter { typeReference ->
                val typeElement = typeReference.typeElement
                val type = context[BindingContext.TYPE, typeReference]
                typeElement != null && type?.constructor?.declarationDescriptor == parameterDescriptor
            }.map { it.createSmartPointer() }
            .toList()

        return InlineTypeParameterFix(element, typeReferencesToInline).asIntention()
    }
}