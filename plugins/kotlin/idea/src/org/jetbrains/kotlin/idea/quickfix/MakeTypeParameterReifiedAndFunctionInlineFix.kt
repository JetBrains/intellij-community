// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class MakeTypeParameterReifiedAndFunctionInlineFix(
    element: KtTypeParameter,
) : PsiUpdateModCommandAction<KtTypeParameter>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("make.type.parameter.reified.and.function.inline")

    override fun invoke(
        context: ActionContext,
        element: KtTypeParameter,
        updater: ModPsiUpdater,
    ) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        function.addModifier(KtTokens.INLINE_KEYWORD)
        element.addModifier(KtTokens.REIFIED_KEYWORD)
    }

    companion object : KotlinSingleIntentionActionFactory() {
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

}
