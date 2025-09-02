// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

class ReplaceInvokeIntention : KotlinApplicableModCommandAction<KtDotQualifiedExpression, Unit>(KtDotQualifiedExpression::class) {

    override fun getPresentation(
        context: ActionContext,
        element: KtDotQualifiedExpression
    ): Presentation {
        return Presentation.of(familyName).withPriority(PriorityAction.Priority.HIGH)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.invoke.with.direct.call")

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> {
        val selectorExpression = element.callExpression ?: return emptyList()
        val calleeExpression = selectorExpression.calleeExpression ?: return emptyList()
        return listOf(calleeExpression.textRange.shiftLeft(element.startOffset))
    }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val selectorExpression = element.callExpression ?: return false
        val calleeExpression = selectorExpression.calleeExpression ?: return false
        return calleeExpression.text == OperatorNameConventions.INVOKE.asString() &&
                selectorExpression.typeArgumentList == null
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        val resolvedCall = element.callExpression?.referenceExpression()?.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val symbol = resolvedCall.symbol as? KaNamedFunctionSymbol ?: return null

        return symbol.isOperator.asUnit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtDotQualifiedExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        OperatorToFunctionConverter.replaceExplicitInvokeCallWithImplicit(element)
    }
}