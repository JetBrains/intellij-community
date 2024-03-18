// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
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
  AbstractKotlinApplicableInspectionWithContext<KtDotQualifiedExpression, ReplaceGetOrSetInspection.Context>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
    class Context(val calleeName: Name, val problemHighlightType: ProblemHighlightType)

    override fun getProblemDescription(element: KtDotQualifiedExpression, context: Context): String =
        KotlinBundle.message("explicit.0.call", context.calleeName)

    override fun getActionFamilyName(): String = KotlinBundle.message("replace.get.or.set.call.with.indexing.operator")

    override fun getActionName(element: KtDotQualifiedExpression, context: Context): String =
        KotlinBundle.message("replace.0.call.with.indexing.operator", context.calleeName)

    override fun getApplicabilityRange() = applicabilityRange { dotQualifiedExpression: KtDotQualifiedExpression ->
        dotQualifiedExpression.getPossiblyQualifiedCallExpression()?.calleeExpression?.textRangeIn(dotQualifiedExpression)
    }

    override fun getProblemHighlightType(element: KtDotQualifiedExpression, context: Context): ProblemHighlightType =
        context.problemHighlightType

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean =
        ReplaceGetOrSetInspectionUtils.looksLikeGetOrSetOperatorCall(element)

    context(KtAnalysisSession)
    override fun prepareContext(element: KtDotQualifiedExpression): Context? {
        // `resolveCall()` is needed to filter out `set` functions with varargs or default values. See the `setWithVararg.kt` test.
        val call = element.resolveCall()?.successfulCallOrNull<KtSimpleFunctionCall>() ?: return null
        val functionSymbol = call.symbol
        if (functionSymbol !is KtFunctionSymbol || !functionSymbol.isOperator) {
            return null
        }

        val receiverExpression = element.receiverExpression
        if (receiverExpression is KtSuperExpression || receiverExpression.getKtType()?.isUnit != false) return null

        if (functionSymbol.name == OperatorNameConventions.SET && element.isUsedAsExpression()) return null

        val problemHighlightType = if (functionSymbol.isExplicitOperator()) GENERIC_ERROR_OR_WARNING else INFORMATION

        return Context(functionSymbol.name, problemHighlightType)
    }

    override fun apply(element: KtDotQualifiedExpression, context: Context, project: Project, updater: ModPsiUpdater) {
        ReplaceGetOrSetInspectionUtils.replaceGetOrSetWithPropertyAccessor(
            element,
            isSet = context.calleeName == OperatorNameConventions.SET
        ) { updater.moveCaretTo(it) }
    }

    context(KtAnalysisSession)
    private fun KtFunctionSymbol.isExplicitOperator(): Boolean {
        fun KtCallableSymbol.hasOperatorKeyword() = psiSafe<KtNamedFunction>()?.hasModifier(KtTokens.OPERATOR_KEYWORD) == true
        return hasOperatorKeyword() || getAllOverriddenSymbols().any { it.hasOperatorKeyword() }
    }
}