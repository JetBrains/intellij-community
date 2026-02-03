// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ReplaceGetOrSetInspectionUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class ReplaceGetOrSetInspection :
    KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, ReplaceGetOrSetInspection.Context>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {
        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    data class Context(
        val calleeName: Name,
        val problemHighlightType: ProblemHighlightType,
    )

    override fun getProblemDescription(element: KtDotQualifiedExpression, context: Context): String =
        KotlinBundle.message("explicit.0.call", context.calleeName)

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> {
        val textRange = element.getPossiblyQualifiedCallExpression()
            ?.calleeExpression
            ?.textRangeIn(element)
        return listOfNotNull(textRange)
    }

    override fun getProblemHighlightType(
        element: KtDotQualifiedExpression,
        context: Context
    ): ProblemHighlightType = context.problemHighlightType

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean =
        ReplaceGetOrSetInspectionUtils.looksLikeGetOrSetOperatorCall(element)

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Context? {
        // `resolveCallOld()` is needed to filter out `set` functions with varargs or default values. See the `setWithVararg.kt` test.
        val call = element.resolveToCall()?.successfulCallOrNull<KaSimpleFunctionCall>() ?: return null
        val functionSymbol = call.symbol
        if (functionSymbol !is KaNamedFunctionSymbol || !functionSymbol.isOperator) {
            return null
        }

        if (functionSymbol.name != OperatorNameConventions.GET && functionSymbol.name != OperatorNameConventions.SET) return null

        val receiverExpression = element.receiverExpression
        if (receiverExpression is KtSuperExpression || receiverExpression.expressionType?.isUnitType != false) return null

        if (functionSymbol.name == OperatorNameConventions.SET && element.isUsedAsExpression) return null

        val problemHighlightType = if (functionSymbol.isExplicitOperator()) GENERIC_ERROR_OR_WARNING else INFORMATION

        return Context(functionSymbol.name, problemHighlightType)
    }

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("replace.get.or.set.call.with.indexing.operator")

        override fun getName(): String =
            KotlinBundle.message("replace.0.call.with.indexing.operator", context.calleeName)

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            ReplaceGetOrSetInspectionUtils.replaceGetOrSetWithPropertyAccessor(
                element,
                isSet = context.calleeName == OperatorNameConventions.SET,
            ) { updater.moveCaretTo(it) }
        }
    }

    context(_: KaSession)
    private fun KaNamedFunctionSymbol.isExplicitOperator(): Boolean {
        fun KaCallableSymbol.hasOperatorKeyword() = psiSafe<KtNamedFunction>()?.hasModifier(KtTokens.OPERATOR_KEYWORD) == true
        return allOverriddenSymbolsWithSelf.any { it.hasOperatorKeyword() }
    }
}