// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object MakeTypeParameterReifiedAndFunctionInlineFixFactory {
    val cannotCheckForErasedFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.CannotCheckForErased ->
        val typeReference = diagnostic.psi as? KtTypeReference ?: return@ModCommandBased emptyList()
        val function = typeReference.getStrictParentOfType<KtNamedFunction>() ?: return@ModCommandBased emptyList()
        val typeParameter = function.typeParameterList?.parameters?.firstOrNull {
            it.getSymbol() == (diagnostic.type as? KtTypeParameterType)?.symbol
        } ?: return@ModCommandBased emptyList()
        val elementContext = ElementContext(typeParameter.createSmartPointer())
        listOf(MakeTypeParameterReifiedAndFunctionInlineFix(function, elementContext))
    }

    private data class ElementContext(
        val typeParameter: SmartPsiElementPointer<KtTypeParameter>,
    )

    private class MakeTypeParameterReifiedAndFunctionInlineFix(
        element: KtNamedFunction,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtNamedFunction, ElementContext>(element, elementContext) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtNamedFunction,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val typeParameter = updater.getWritable(elementContext.typeParameter.element) ?: return
            element.addModifier(KtTokens.INLINE_KEYWORD)
            typeParameter.addModifier(KtTokens.REIFIED_KEYWORD)
        }

        override fun getFamilyName(): String = KotlinBundle.message("make.type.parameter.reified.and.function.inline")
    }
}