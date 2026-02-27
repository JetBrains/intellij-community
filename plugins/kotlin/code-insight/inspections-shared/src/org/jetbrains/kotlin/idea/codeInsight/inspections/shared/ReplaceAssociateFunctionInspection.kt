// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction.ASSOCIATE_BY_KEY_AND_VALUE
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction.ASSOCIATE_BY
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction.ASSOCIATE_WITH
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunctionUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunctionUtil.lambda
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunctionUtil.lastStatement
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunctionUtil.pair
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds.BASE_COLLECTIONS_PACKAGE
import org.jetbrains.kotlin.psi.BuilderByPattern
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.buildExpression
import org.jetbrains.kotlin.psi.dotQualifiedExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds.BASE_SEQUENCES_PACKAGE


class ReplaceAssociateFunctionInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = dotQualifiedExpressionVisitor(fun(dotQualifiedExpression) {
        val callExpression = dotQualifiedExpression.callExpression ?: return
        val calleeExpression = callExpression.calleeExpression ?: return
        val calleeExpressionFqName = analyze(calleeExpression) {
            val functionCall = calleeExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return
            functionCall.symbol.callableId?.asSingleFqName() ?: return
        }
        if (calleeExpressionFqName !in validAssociateFqNames) return

        val isAssociateTo = calleeExpression.text == "associateTo"
        val (associateFunction, highlightType) = AssociateFunctionUtil.getAssociateFunctionAndProblemHighlightType(dotQualifiedExpression) ?: return
        holder.registerProblemWithoutOfflineInformation(
            calleeExpression,
            KotlinBundle.message("replace.0.with.1", calleeExpression.text, associateFunction.name(isAssociateTo)),
            isOnTheFly,
            highlightType,
            ReplaceAssociateFunctionFix(associateFunction, isAssociateTo)
        )
    })
}

class ReplaceAssociateFunctionFix(
    private val function: AssociateFunction,
    private val hasDestination: Boolean,
) : KotlinModCommandQuickFix<KtExpression>() {
    private val functionName = function.name(hasDestination)

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.0", functionName)

    override fun applyFix(
        project: Project,
        element: KtExpression,
        updater: ModPsiUpdater,
    ) {
        val dotQualifiedExpression = element.getStrictParentOfType<KtDotQualifiedExpression>() ?: return
        val receiverExpression = dotQualifiedExpression.receiverExpression
        val callExpression = dotQualifiedExpression.callExpression ?: return
        val lambda = callExpression.lambda() ?: return
        val lastStatement = lambda.functionLiteral.lastStatement() ?: return
        val (keySelector, valueTransform) =
            analyze(lastStatement) { pair(lastStatement) } ?: return

        val psiFactory = KtPsiFactory(project)

        val expressionData = ExpressionData(callExpression, receiverExpression, lastStatement, keySelector, valueTransform, lambda, psiFactory)

        val newExpression = when (function) {
            ASSOCIATE_BY -> createAssociateByExpression(expressionData)
            ASSOCIATE_WITH -> createAssociateWithExpression(expressionData)
            ASSOCIATE_BY_KEY_AND_VALUE -> createAssociateByKeyAndValueExpression(expressionData)
        }
        dotQualifiedExpression.replace(newExpression)
    }

    private fun createAssociateWithExpression(expressionData: ExpressionData): KtExpression {
        with(expressionData) {
            lastStatement.replace(valueTransform)
            return psiFactory.buildExpression {
                appendExpression(receiverExpression)
                appendFixedText(".")
                appendFixedText(functionName)
                callExpression.valueArgumentList?.let { appendValueArgumentList(it) }
                if (callExpression.lambdaArguments.isNotEmpty()) appendLambda(lambda)
            }
        }
    }

    private fun createAssociateByExpression(expressionData: ExpressionData): KtExpression {
        with(expressionData) {
            lastStatement.replace(keySelector)
            return psiFactory.buildExpression {
                appendExpression(receiverExpression)
                appendFixedText(".")
                appendFixedText(functionName)
                callExpression.valueArgumentList?.let { appendValueArgumentList(it) }
                if (callExpression.lambdaArguments.isNotEmpty()) appendLambda(lambda)
            }
        }
    }

    private fun createAssociateByKeyAndValueExpression(expressionData: ExpressionData): KtExpression {
        with(expressionData) {
            val destination = callExpression.valueArguments
                .firstOrNull()
                ?.getArgumentExpression()
                .takeIf { hasDestination }
            return psiFactory.buildExpression {
                appendExpression(receiverExpression)
                appendFixedText(".")
                appendFixedText(functionName)
                appendFixedText("(")
                if (destination != null) {
                    appendExpression(destination)
                    appendFixedText(",")
                }
                appendLambda(lambda, keySelector)
                appendFixedText(",")
                appendLambda(lambda, valueTransform)
                appendFixedText(")")
            }
        }
    }

    private fun BuilderByPattern<KtExpression>.appendLambda(lambda: KtLambdaExpression, body: KtExpression? = null) {
        appendFixedText("{")
        lambda.valueParameters.firstOrNull()?.nameAsName?.also {
            appendName(it)
            appendFixedText("->")
        }

        if (body != null) {
            appendExpression(body)
        } else {
            lambda.bodyExpression?.allChildren?.let(this::appendChildRange)
        }

        appendFixedText("}")
    }

    private fun BuilderByPattern<KtExpression>.appendValueArgumentList(valueArgumentList: KtValueArgumentList) {
        appendFixedText("(")
        valueArgumentList.arguments.forEachIndexed { index, argument ->
            if (index > 0) appendFixedText(",")
            appendExpression(argument.getArgumentExpression())
        }
        appendFixedText(")")
    }

    companion object {
        fun replaceLastStatementForAssociateFunction(callExpression: KtCallExpression, function: AssociateFunction) {
            val lastStatement = callExpression.lambda()?.functionLiteral?.lastStatement() ?: return
            val (keySelector, valueTransform) = analyze(lastStatement) {
                pair(lastStatement)
            } ?: return
            lastStatement.replace(if (function == ASSOCIATE_WITH) valueTransform else keySelector)
        }
    }

    private data class ExpressionData(
        val callExpression: KtCallExpression,
        val receiverExpression: KtExpression,
        val lastStatement: KtExpression,
        val keySelector: KtExpression,
        val valueTransform: KtExpression,
        val lambda: KtLambdaExpression,
        val psiFactory: KtPsiFactory,
    )
}

private val validAssociateFqNames: List<FqName> = listOf(
    BASE_COLLECTIONS_PACKAGE.child(Name.identifier("associate")),
    BASE_COLLECTIONS_PACKAGE.child(Name.identifier("associateTo")),
    BASE_SEQUENCES_PACKAGE.child(Name.identifier("associate")),
    BASE_SEQUENCES_PACKAGE.child(Name.identifier("associateTo")),
)