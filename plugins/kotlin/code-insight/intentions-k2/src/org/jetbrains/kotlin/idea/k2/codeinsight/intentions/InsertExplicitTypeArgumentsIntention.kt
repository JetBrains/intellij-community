// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTarget
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.types.Variance

class InsertExplicitTypeArgumentsIntention :
    AbstractKotlinApplicableIntentionWithContext<KtCallExpression, String>(KtCallExpression::class) {

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtCallExpression> = applicabilityTarget { it.calleeExpression }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean = element.typeArguments.isEmpty() && element.calleeExpression != null

    override fun getFamilyName(): String = KotlinBundle.message("add.explicit.type.arguments")

    override fun getActionName(element: KtCallExpression, context: String): String = familyName

    context(KtAnalysisSession)
    override fun prepareContext(element: KtCallExpression): String? {
        val resolvedCall = element.resolveCall().singleFunctionCallOrNull() ?: return null
        val typeParameterSymbols = resolvedCall.partiallyAppliedSymbol.symbol.typeParameters
        if (typeParameterSymbols.isEmpty()) return null
        val renderedTypeParameters = buildList {
            for (symbol in typeParameterSymbols) {
                val type = resolvedCall.typeArgumentsMapping[symbol]
                if (type == null || type.containsErrorType() || !type.isDenotable) return null
                add(type.render(position = Variance.OUT_VARIANCE))
            }
        }

        return renderedTypeParameters.joinToString(separator = ", ", prefix = "<", postfix = ">")
    }

    override fun apply(element: KtCallExpression, context: String, project: Project, editor: Editor?) {
        val callee = element.calleeExpression ?: return
        val argumentList = KtPsiFactory(element).createTypeArguments(context)
        val newArgumentList = element.addAfter(argumentList, callee) as KtTypeArgumentList
        ShortenReferencesFacility.getInstance().shorten(newArgumentList)
    }
}

context(KtAnalysisSession)
private fun KtType.containsErrorType(): Boolean = when (this) {
    is KtClassErrorType -> true
    is KtTypeErrorType -> true
    is KtFunctionalType -> {
        (receiverType?.containsErrorType() == true)
                || returnType.containsErrorType()
                || parameterTypes.any { it.containsErrorType() }
                || ownTypeArguments.any { it.type?.containsErrorType() == true }
    }
    is KtNonErrorClassType -> ownTypeArguments.any { it.type?.containsErrorType() == true }
    is KtDefinitelyNotNullType -> original.containsErrorType()
    is KtFlexibleType -> lowerBound.containsErrorType() || upperBound.containsErrorType()
    is KtIntersectionType -> conjuncts.any { it.containsErrorType() }
    is KtTypeParameterType, is KtCapturedType, is KtIntegerLiteralType, is KtDynamicType -> false
}
