// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.invertSelectorFunction
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.canBeInverted
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.negate
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ConvertFunctionWithDemorgansLawIntention.ConvertFunctionWithDemorgansLawContext
import org.jetbrains.kotlin.idea.refactoring.appendCallOrQualifiedExpression
import org.jetbrains.kotlin.idea.refactoring.singleLambdaArgumentExpression
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds.BASE_COLLECTIONS_PACKAGE
import org.jetbrains.kotlin.name.StandardClassIds.BASE_SEQUENCES_PACKAGE
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal sealed class ConvertFunctionWithDemorgansLawIntention(
    conversions: List<Conversion>,
) : KotlinApplicableModCommandAction<KtCallExpression, ConvertFunctionWithDemorgansLawContext>(KtCallExpression::class) {
    private val conversions = conversions.associateBy { it.fromFunctionName }

    class ConvertFunctionWithDemorgansLawContext(
        /**
         * negated operands, non-physical
         */
        val negatedOperands: List<KtExpression>?,
        /**
         * true if parent (parenthesis skipped) is a call to kotlin.Boolean.not
         */
        val parentNotCall: Boolean,
    )

    override fun getPresentation(
        context: ActionContext, element: KtCallExpression
    ): Presentation? {
        val (fromFunctionName, toFunctionName, _, _) = conversions[element.calleeExpression?.text] ?: return null
        return Presentation.of(KotlinBundle.message("replace.0.with.1", fromFunctionName, toFunctionName))
    }

    override fun KaSession.prepareContext(element: KtCallExpression): ConvertFunctionWithDemorgansLawContext? {
        val (fromFunctionName, _, _, negatePredicate) = conversions[element.calleeExpression?.text] ?: return null
        val fqNames = functions[fromFunctionName] ?: return null
        val targetFunctionName =
            element.resolveToCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.importableFqName ?: return null
        if (targetFunctionName !in fqNames) return null

        val lambda = element.singleLambdaArgumentExpression() ?: return null
        val lastStatement = lambda.bodyExpression?.statements?.lastOrNull() ?: return null
        if (lambda.anyDescendantOfType<KtReturnExpression> { it != lastStatement }) return null

        val functionPredicate = when (lastStatement) {
            is KtReturnExpression -> {
                val targetSymbol = lastStatement.targetSymbol
                val lambdaSymbol = lambda.functionLiteral.symbol
                if (targetSymbol == lambdaSymbol) lastStatement.returnedExpression else null
            }

            else -> lastStatement
        } ?: return null

        if (functionPredicate.expressionType?.isBooleanType != true) return null

        val callOrQualified = element.getQualifiedExpressionForSelectorOrThis()
        val skippedParenthesisUp = callOrQualified.parents.dropWhile { it is KtParenthesizedExpression }.firstOrNull()
        val parentNotCall =
            (skippedParenthesisUp as? KtQualifiedExpression)
                ?.callExpression
                ?.isCallingAnyOf(StandardKotlinNames.Boolean.not) == true

        val operands = if (negatePredicate) {
            negate(functionPredicate)?.takeIf { it.isNotEmpty() } ?: return null
        } else {
            null
        }
        return ConvertFunctionWithDemorgansLawContext(operands, parentNotCall)
    }

    private fun negate(baseExpression: KtExpression): List<KtExpression>? {
        fun negateOperand(operand: KtExpression): KtExpression {
            return (operand as? KtQualifiedExpression)?.invertSelectorFunction()
                ?: operand.negate(reformat = false) { analyze(it) { it.expressionType?.isBooleanType == true }}
        }

        return when (baseExpression) {
            is KtBinaryExpression -> {
                val operationToken = baseExpression.operationToken
                if (operationToken == KtTokens.ANDAND || operationToken == KtTokens.OROR) {
                    DemorgansLawUtils.getOperandsIfAllBoolean(baseExpression)?.reversed()?.mapNotNull { negateOperand(it) }
                } else {
                    if ((operationToken as? KtSingleValueToken)?.negate() == null) return null
                    val negated = if (baseExpression.canBeInverted()) {
                        negateOperand(baseExpression)
                    } else {
                        KtPsiFactory(baseExpression.project).createExpressionByPattern("!($0)", baseExpression)
                    }
                    listOfNotNull(negated)
                }
            }

            is KtQualifiedExpression -> listOfNotNull(negateOperand(baseExpression))

            is KtPrefixExpression -> listOfNotNull(negateOperand(baseExpression))

            else -> null
        }
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: ConvertFunctionWithDemorgansLawContext,
        updater: ModPsiUpdater
    ) {
        val (_, toFunctionName, negateCall, negatePredicate) = conversions[element.calleeExpression?.text] ?: return
        val lambda = element.singleLambdaArgumentExpression() ?: return
        val lastStatement = lambda.bodyExpression?.statements?.lastOrNull() ?: return
        val returnExpression = lastStatement.safeAs<KtReturnExpression>()
        val predicate = returnExpression?.returnedExpression ?: lastStatement

        val psiFactory = KtPsiFactory(element.project)

        if (negatePredicate) {
            val oppositeOperand = when ((predicate as? KtBinaryExpression)?.operationToken) {
                KtTokens.ANDAND -> KtTokens.OROR
                KtTokens.OROR -> KtTokens.ANDAND
                else -> null
            }
            val negatedOperands = elementContext.negatedOperands!!
            val negated = if (oppositeOperand != null) {
                predicate as KtBinaryExpression
                psiFactory.buildExpression {
                    appendExpressions(negatedOperands, separator = oppositeOperand.value)
                }
            } else {
                if (negatedOperands.size != 1) {
                    logger<ConvertFunctionWithDemorgansLawIntention>().error("Found operands ${negatedOperands.size}")
                }
                negatedOperands.firstOrNull()!!
            }
            predicate.replaced(negated)
        }

        if (returnExpression?.getLabelName() == element.calleeExpression?.text) {
            returnExpression?.labelQualifier?.replace(psiFactory.createLabelQualifier(toFunctionName))
        }
        val callOrQualified = element.getQualifiedExpressionForSelectorOrThis()
        val parentNegatedExpression = callOrQualified.parentNegatedExpression(elementContext.parentNotCall)
        psiFactory.buildExpression {
            val addNegation = negateCall && parentNegatedExpression == null
            if (addNegation && callOrQualified !is KtSafeQualifiedExpression) {
                appendFixedText("!")
            }
            appendCallOrQualifiedExpression(element, toFunctionName)
            if (addNegation && callOrQualified is KtSafeQualifiedExpression) {
                appendFixedText("?.not()")
            }
        }.let { (parentNegatedExpression ?: callOrQualified).replaced(it) }
    }

    private fun KtExpression.parentNegatedExpression(parentNotCall: Boolean): KtExpression? {
        val parent = parents.dropWhile { it is KtParenthesizedExpression }.firstOrNull() ?: return null
        return parent.asExclPrefixExpression() ?: if (parentNotCall) parent as? KtQualifiedExpression else null
    }

    private fun PsiElement.asExclPrefixExpression(): KtPrefixExpression? {
        return safeAs<KtPrefixExpression>()?.takeIf { it.operationToken == KtTokens.EXCL && it.baseExpression != null }
    }

    private fun KtPsiFactory.createLabelQualifier(labelName: String): KtContainerNode {
        return (createExpression("return@$labelName 1") as KtReturnExpression).labelQualifier!!
    }
}

private val collectionFunctions: Map<String, List<FqName>> =
    listOf("all", "any", "none", "filter", "filterNot", "filterTo", "filterNotTo").associateWith {
        listOf(BASE_COLLECTIONS_PACKAGE + it, BASE_SEQUENCES_PACKAGE + it)
    }

private val standardFunctions: Map<String, List<FqName>> = listOf(StandardKotlinNames.takeIf, StandardKotlinNames.takeUnless).associate {
    it.shortName().asString() to listOf(it)
}

private val functions: Map<String, List<FqName>> = collectionFunctions + standardFunctions

private data class Conversion(
    val fromFunctionName: String, val toFunctionName: String, val negateCall: Boolean, val negatePredicate: Boolean
)

internal class ConvertCallToOppositeIntention : ConvertFunctionWithDemorgansLawIntention(
    listOf(
        Conversion("all", "none", false, true),
        Conversion("none", "all", false, true),
        Conversion("filter", "filterNot", false, true),
        Conversion("filterNot", "filter", false, true),
        Conversion("filterTo", "filterNotTo", false, true),
        Conversion("filterNotTo", "filterTo", false, true),
        Conversion("takeIf", "takeUnless", false, true),
        Conversion("takeUnless", "takeIf", false, true)
    )
) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.function.call.with.the.opposite")
}

internal class ConvertAnyToAllAndViceVersaIntention : ConvertFunctionWithDemorgansLawIntention(
    listOf(
        Conversion("any", "all", true, true), Conversion("all", "any", true, true)
    )
) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.0.with.1.and.vice.versa", "any", "all")
}

internal class ConvertAnyToNoneAndViceVersaIntention : ConvertFunctionWithDemorgansLawIntention(
    listOf(
        Conversion("any", "none", true, false), Conversion("none", "any", true, false)
    )
) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.0.with.1.and.vice.versa", "any", "none")
}