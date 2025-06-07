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
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction.ASSOCIATE_WITH
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunctionUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunctionUtil.lambda
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunctionUtil.lastStatement
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunctionUtil.pair
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds.BASE_COLLECTIONS_PACKAGE
import org.jetbrains.kotlin.name.StandardClassIds.BASE_SEQUENCES_PACKAGE
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

private val associateFunctionNames: List<String> = listOf("associate", "associateTo")
private val associateFqNames: Set<FqName> =
    arrayOf(BASE_COLLECTIONS_PACKAGE, BASE_SEQUENCES_PACKAGE).mapTo(hashSetOf()) { it.child(Name.identifier("associate")) }
private val associateToFqNames: Set<FqName> =
    arrayOf(BASE_COLLECTIONS_PACKAGE, BASE_SEQUENCES_PACKAGE).mapTo(hashSetOf()) { it.child(Name.identifier("associateTo")) }

class ReplaceAssociateFunctionInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = dotQualifiedExpressionVisitor(fun(dotQualifiedExpression) {
        val callExpression = dotQualifiedExpression.callExpression ?: return
        val calleeExpression = callExpression.calleeExpression ?: return
        if (calleeExpression.text !in associateFunctionNames) return

        val fqName = analyze(dotQualifiedExpression) {
            val functionCall = dotQualifiedExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return
            functionCall.symbol.callableId?.asSingleFqName() ?: return
        }
        val isAssociate = fqName in associateFqNames
        val isAssociateTo = fqName in associateToFqNames
        if (!isAssociate && !isAssociateTo) return

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

    override fun getName(): String = KotlinBundle.message("replace.with.0", functionName)

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
        if (function == ASSOCIATE_BY_KEY_AND_VALUE) {
            val destination = if (hasDestination) {
                callExpression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
            } else {
                null
            }
            val newExpression = psiFactory.buildExpression {
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
            dotQualifiedExpression.replace(newExpression)
        } else {
            lastStatement.replace(if (function == ASSOCIATE_WITH) valueTransform else keySelector)
            val newExpression = psiFactory.buildExpression {
                appendExpression(receiverExpression)
                appendFixedText(".")
                appendFixedText(functionName)
                callExpression.valueArgumentList?.let { valueArgumentList ->
                    appendValueArgumentList(valueArgumentList)
                }
                if (callExpression.lambdaArguments.isNotEmpty()) {
                    appendLambda(lambda)
                }
            }
            dotQualifiedExpression.replace(newExpression)
        }
    }

    override fun getFamilyName(): String = name

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
            val (keySelector, valueTransform) = analyze<Pair<KtExpression, KtExpression>?>(lastStatement) {
                pair(lastStatement)
            } ?: return
            lastStatement.replace(if (function == ASSOCIATE_WITH) valueTransform else keySelector)
        }
    }
}