// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

internal class ConvertVarargParameterToArrayIntention :
    KotlinApplicableModCommandAction<KtParameter, ConvertVarargParameterToArrayIntention.Context>(KtParameter::class) {
    class Context(val renderedParameterType: String)

    override fun invoke(
        actionContext: ActionContext,
        element: KtParameter,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        val typeReference = element.getChildOfType<KtTypeReference>() ?: return

        typeReference.replace(KtPsiFactory(element.project).createType(elementContext.renderedParameterType))
        element.removeModifier(KtTokens.VARARG_KEYWORD)
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.to.array.parameter")

    override fun isApplicableByPsi(element: KtParameter): Boolean {
        return element.getChildOfType<KtTypeReference>() != null && element.isVarArg
    }

    override fun KaSession.prepareContext(element: KtParameter): Context? {
        val typeReference = element.getChildOfType<KtTypeReference>() ?: return null
        val renderedParameterType = analyze(element) {
            val parameterType = element.symbol.returnType
            if (parameterType.isPrimitive) {
                "${typeReference.text}Array"
            } else {
                "Array<${typeReference.text}>"
            }
        }
        return Context(renderedParameterType)
    }
}