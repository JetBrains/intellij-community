// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.types.Variance
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

        val expressionData = ExpressionData(callExpression, receiverExpression, lastStatement, keySelector, valueTransform, lambda, KtPsiFactory(project))

        val newExpression = when (function) {
            ASSOCIATE_BY -> createAssociateByExpression(expressionData)
            ASSOCIATE_WITH -> createAssociateWithExpression(expressionData, updater)
            ASSOCIATE_BY_KEY_AND_VALUE -> createAssociateByKeyAndValueExpression(expressionData)
        }
        newExpression?.let { dotQualifiedExpression.replace(newExpression) }
    }

    @OptIn(KaExperimentalApi::class)
    private fun createAssociateWithExpression(expressionData: ExpressionData, updater: ModPsiUpdater): KtExpression? =
        with(expressionData) {
            var keySelectorTypeRendered: String? = null
            val isSubtype: Boolean = analyze(callExpression) {
                val expectedType = callExpression.getStrictParentOfType<KtDotQualifiedExpression>()?.expressionType as? KaClassType
                if (expectedType == null) return null
                val typeArguments = expectedType.typeArguments
                if (typeArguments.size != 2) return@with null

                val expectedKeyType = typeArguments[0].type ?: return@with null
                val keySelectorType = keySelector.expressionType ?: return@with null
                keySelectorTypeRendered = keySelectorType.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)

                if (keySelectorType.semanticallyEquals(expectedKeyType)) false
                else if (keySelectorType.isSubtypeOf(expectedKeyType)) true//need to be mentioned explicitly
                else return@with null
            }

            if (isSubtype) {
                val paramName = lambda.valueParameters.firstOrNull()?.text ?: "it"
                createTypeCastedExpression(valueTransform, paramName, keySelectorTypeRendered, psiFactory, updater)
            }

            lastStatement.replace(valueTransform)
            return buildAssocFunctionExpression()
        }

    private fun createAssociateByExpression(expressionData: ExpressionData): KtExpression {
        with(expressionData) {
            lastStatement.replace(keySelector)
            return buildAssocFunctionExpression()
        }
    }

    private fun ExpressionData.buildAssocFunctionExpression(): KtExpression =
        psiFactory.buildExpression {
            appendExpression(receiverExpression)
            appendFixedText(".")
            appendFixedText(functionName)
            callExpression.valueArgumentList?.let { appendValueArgumentList(it) }
            if (callExpression.lambdaArguments.isNotEmpty()) appendLambda(lambda)
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

    private fun createTypeCastedExpression(valueTransform: KtExpression, paramName: String, keySelectorType: String?, psiFactory: KtPsiFactory, updater: ModPsiUpdater) {
        val expression = when (valueTransform) {
            is KtCallExpression -> valueTransform.valueArguments.find { it.text == paramName } ?: return
            is KtDotQualifiedExpression -> valueTransform.receiverExpression
            else -> return
        }
        val expr = updater
            .getWritable(expression)
            .replace(psiFactory.createExpression("$paramName as $keySelectorType")) as KtExpression
        shortenReferences(expr)
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

private val validAssociateFqNames: List<FqName> = buildList {
    val associate = Name.identifier("associate")
    val associateTo = Name.identifier("associateTo")
    add(BASE_COLLECTIONS_PACKAGE.child(associate))
    add(BASE_COLLECTIONS_PACKAGE.child(associateTo))
    add(BASE_SEQUENCES_PACKAGE.child(associate))
    add(BASE_SEQUENCES_PACKAGE.child(associateTo))
}
