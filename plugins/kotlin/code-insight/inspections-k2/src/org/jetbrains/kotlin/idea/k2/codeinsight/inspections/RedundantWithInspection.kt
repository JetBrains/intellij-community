// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

class RedundantWithInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, RedundantWithInspection.Context>() {

    data class Context(val receiver: KtExpression, val typeReference: KtTypeReference?)

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String = KotlinBundle.message("remove.redundant.with.fix.text")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val lambdaExpression = element.valueArguments.getOrNull(1)?.getLambdaExpression() ?: return
            val lambdaBody = lambdaExpression.bodyExpression ?: return

            val function = element.getStrictParentOfType<KtFunction>()
            val functionBody = function?.bodyExpression

            val replaced = if (functionBody?.safeDeparenthesize() == element) {
                val singleStatement = lambdaBody.statements.singleOrNull()
                if (singleStatement != null) {
                    element.replaced((singleStatement as? KtReturnExpression)?.returnedExpression ?: singleStatement)
                } else {
                    function.setTypeReference(context.typeReference)?.let { shortenReferences(it) }
                    val lambdaStatements = lambdaBody.statements
                    val lastStatement = lambdaStatements.lastOrNull()
                    if (lastStatement != null && lastStatement !is KtReturnExpression) {
                        lastStatement.replaced(KtPsiFactory(project).createExpressionByPattern("return $0", lastStatement))
                    }

                    function.equalsToken?.delete()

                    functionBody.replace(KtPsiFactory.contextual(function).createSingleStatementBlock(lambdaBody))
                }
            } else {
                element.replace(lambdaBody)
            } ?: return

            updater.moveCaretTo(replaced)
        }
    }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context,
    ): String = KotlinBundle.message("inspection.redundant.with.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> = ApplicabilityRanges.calleeExpression(element)

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val callee = element.calleeExpression ?: return false
        if (callee.text != "with") return false

        val valueArguments = element.valueArguments
        if (valueArguments.size != 2) return false
        val receiver = valueArguments[0].getArgumentExpression()
        if (receiver == null || receiver !is KtSimpleNameExpression && receiver !is KtStringTemplateExpression && receiver !is KtConstantExpression) return false
        val lambda = valueArguments[1].getLambdaExpression() ?: return false
        val lambdaBody = lambda.bodyExpression
        return lambdaBody != null
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val callee = element.calleeExpression ?: return null

        val valueArguments = element.valueArguments
        if (valueArguments.size != 2) return null
        val receiver = valueArguments[0].getArgumentExpression() ?: return null
        val lambda = valueArguments[1].getLambdaExpression() ?: return null
        val lambdaBody = lambda.bodyExpression ?: return null

        val call = callee.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        if (call.partiallyAppliedSymbol.signature.callableId?.asSingleFqName() != FqName("kotlin.with")) return null

        val functionLiteral = lambda.functionLiteral
        val used = functionLiteral.anyDescendantOfType<KtElement> {
            (it as? KtReturnExpression)?.getLabelName() == "with"
        } || isReceiverUsedInside(functionLiteral, emptySet())

        if (!used) {
            if (lambdaBody.statements.size > 1 && element.isUsedAsExpression && element.getStrictParentOfType<KtFunction>()?.bodyExpression?.safeDeparenthesize() != element) return null

            val function = element.getStrictParentOfType<KtFunction>()
            return Context(receiver, function?.let {
                it.typeReference ?: KtPsiFactory.contextual(function)
                    .createType(function.returnType.render(position = Variance.OUT_VARIANCE))
            })
        }

        return null
    }

    private fun KtValueArgument.getLambdaExpression(): KtLambdaExpression? =
        (this as? KtLambdaArgument)?.getLambdaExpression() ?: this.getArgumentExpression() as? KtLambdaExpression
}
