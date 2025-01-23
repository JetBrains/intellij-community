// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AssociateFunction.*
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds.BASE_COLLECTIONS_PACKAGE
import org.jetbrains.kotlin.name.StandardClassIds.BASE_SEQUENCES_PACKAGE
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

private val associateFunctionNames: List<String> = listOf("associate", "associateTo")
private val associateFqNames: Set<FqName> =
    arrayOf(BASE_COLLECTIONS_PACKAGE, BASE_SEQUENCES_PACKAGE).mapTo(hashSetOf()) { it.child(Name.identifier("associate")) }
private val associateToFqNames: Set<FqName> =
    arrayOf(BASE_COLLECTIONS_PACKAGE, BASE_SEQUENCES_PACKAGE).mapTo(hashSetOf()) { it.child(Name.identifier("associateTo")) }
private val PAIR_CLASS_ID =
    ClassId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("Pair"))

class ReplaceAssociateFunctionInspection : AbstractKotlinInspection() {

    object Util {
        fun getAssociateFunctionAndProblemHighlightType(
            dotQualifiedExpression: KtDotQualifiedExpression,
        ): Pair<AssociateFunction, ProblemHighlightType>? {
            val callExpression = dotQualifiedExpression.callExpression ?: return null
            val lambda = callExpression.lambda() ?: return null
            if (lambda.valueParameters.size > 1) return null
            val functionLiteral = lambda.functionLiteral
            if (functionLiteral.anyDescendantOfType<KtReturnExpression> { it.labelQualifier != null }) return null
            val lastStatement = functionLiteral.lastStatement() ?: return null
            analyze(dotQualifiedExpression) {
                val (keySelector, valueTransform) = lastStatement.pair() ?: return null
                val lambdaParameter: KaValueParameterSymbol = functionLiteral.symbol.valueParameters.singleOrNull() ?: return null
                return when {
                    keySelector.isReferenceTo(lambdaParameter) -> {
                        val memberCall = dotQualifiedExpression.receiverExpression.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()
                        val receiverType = memberCall?.symbol?.returnType ?: return null
                        if (receiverType.isArrayOrPrimitiveArray &&
                            dotQualifiedExpression.languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_1_4
                        ) return null
                        ASSOCIATE_WITH to GENERIC_ERROR_OR_WARNING
                    }
                    valueTransform.isReferenceTo(lambdaParameter) ->
                        ASSOCIATE_BY to GENERIC_ERROR_OR_WARNING
                    else -> {
                        if (functionLiteral.bodyExpression?.statements?.size != 1) return null
                        ASSOCIATE_BY_KEY_AND_VALUE to INFORMATION
                    }
                }
            }
        }

        context(KaSession)
        private fun KtExpression.isReferenceTo(another: KaValueParameterSymbol): Boolean {
            val referenceExpression = this as? KtNameReferenceExpression ?: return false
            val symbol = referenceExpression.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.symbol
            return symbol == another
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = dotQualifiedExpressionVisitor(fun(dotQualifiedExpression) {
        if (dotQualifiedExpression.languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_1_3) return
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

        val (associateFunction, highlightType) = Util.getAssociateFunctionAndProblemHighlightType(dotQualifiedExpression) ?: return
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
        val (keySelector, valueTransform) = analyze(lastStatement) { lastStatement.pair() } ?: return

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
                val valueArgumentList = callExpression.valueArgumentList
                if (valueArgumentList != null) {
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
                lastStatement.pair()
            } ?: return
            lastStatement.replace(if (function == ASSOCIATE_WITH) valueTransform else keySelector)
        }
    }
}

private fun KtCallExpression.lambda(): KtLambdaExpression? {
    return lambdaArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression ?: getLastLambdaExpression()
}

private fun KtFunctionLiteral.lastStatement(): KtExpression? {
    return bodyExpression?.statements?.lastOrNull()
}

context(KaSession)
private fun KtExpression.pair(): Pair<KtExpression, KtExpression>? {
    return when (this) {
        is KtBinaryExpression -> {
            if (operationReference.text != "to") return null
            val left = left ?: return null
            val right = right ?: return null
            left to right
        }
        is KtCallExpression -> {
            if (calleeExpression?.text != "Pair") return null
            if (valueArguments.size != 2) return null
            val constructorSymbol = resolveToCall()?.singleConstructorCallOrNull()?.symbol ?: return null
            val classId = (constructorSymbol.returnType as? KaClassType)?.classId ?: return null
            if (classId != PAIR_CLASS_ID) return null
            val first = valueArguments[0]?.getArgumentExpression() ?: return null
            val second = valueArguments[1]?.getArgumentExpression() ?: return null
            first to second
        }
        else -> return null
    }
}