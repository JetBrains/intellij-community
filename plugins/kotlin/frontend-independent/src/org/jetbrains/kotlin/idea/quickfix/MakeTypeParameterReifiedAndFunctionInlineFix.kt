// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class MakeTypeParameterReifiedAndFunctionInlineFix(
    element: KtTypeParameter,
) : PsiUpdateModCommandAction<KtTypeParameter>(element) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("make.type.parameter.reified.and.function.inline")

    override fun invoke(
        context: ActionContext,
        element: KtTypeParameter,
        updater: ModPsiUpdater,
    ) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        function.addModifier(KtTokens.INLINE_KEYWORD)
        element.addModifier(KtTokens.REIFIED_KEYWORD)
    }
}
