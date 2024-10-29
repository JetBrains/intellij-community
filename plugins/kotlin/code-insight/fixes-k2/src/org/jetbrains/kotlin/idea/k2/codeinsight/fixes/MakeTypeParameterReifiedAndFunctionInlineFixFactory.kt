// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object MakeTypeParameterReifiedAndFunctionInlineFixFactory {
    val cannotCheckForErasedFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.CannotCheckForErased ->
        val typeReference = diagnostic.psi as? KtTypeReference ?: return@ModCommandBased emptyList()
        val function = typeReference.getStrictParentOfType<KtNamedFunction>() ?: return@ModCommandBased emptyList()
        val typeParameter = function.typeParameterList?.parameters?.firstOrNull {
            it.symbol == (diagnostic.type as? KaTypeParameterType)?.symbol
        } ?: return@ModCommandBased emptyList()
        listOf(MakeTypeParameterReifiedAndFunctionInlineFix(typeParameter))
    }

    private class MakeTypeParameterReifiedAndFunctionInlineFix(
        element: KtTypeParameter,
    ) : PsiUpdateModCommandAction<KtTypeParameter>(element) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtTypeParameter,
            updater: ModPsiUpdater,
        ) {
            val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
            function.addModifier(KtTokens.INLINE_KEYWORD)
            element.addModifier(KtTokens.REIFIED_KEYWORD)
        }

        override fun getFamilyName(): String = KotlinBundle.message("make.type.parameter.reified.and.function.inline")
    }
}