// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class RemoveExplicitLambdaParameterTypesIntention : KotlinApplicableModCommandAction<KtLambdaExpression, Unit>(
    KtLambdaExpression::class
) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.explicit.lambda.parameter.types.may.break.code")

    override fun isApplicableByPsi(element: KtLambdaExpression): Boolean {
        return element.valueParameters.any { it.typeReference != null }
    }

    override fun getApplicableRanges(element: KtLambdaExpression): List<TextRange> {
        val arrow = element.functionLiteral.arrow ?: return emptyList()
        return listOf(TextRange(0, arrow.endOffset - element.startOffset))
    }

    override fun KaSession.prepareContext(element: KtLambdaExpression): Unit = Unit

    override fun invoke(
        actionContext: ActionContext,
        element: KtLambdaExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val oldParameterList = element.functionLiteral.valueParameterList!!

        val parameterString = oldParameterList.parameters.asSequence().map {
            it.destructuringDeclaration?.text ?: it.name
        }.joinToString(", ")

        val newParameterList = KtPsiFactory(element.project).createLambdaParameterList(parameterString)
        oldParameterList.replace(newParameterList)
    }
}
