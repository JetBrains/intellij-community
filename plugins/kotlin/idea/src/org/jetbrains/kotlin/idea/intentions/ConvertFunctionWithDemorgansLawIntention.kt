// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils
import org.jetbrains.kotlin.idea.inspections.ReplaceNegatedIsEmptyWithIsNotEmptyInspection.Util.invertSelectorFunction
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.refactoring.appendCallOrQualifiedExpression
import org.jetbrains.kotlin.idea.refactoring.singleLambdaArgumentExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.function.Supplier

internal sealed class ConvertFunctionWithDemorgansLawIntention(
    intentionName: Supplier<@IntentionName String>,
    conversions: List<Conversion>,
) : SelfTargetingRangeIntention<KtCallExpression>(
    KtCallExpression::class.java,
    intentionName
) {
    @SafeFieldForPreview
    private val conversions = conversions.associateBy { it.fromFunctionName }

    override fun applicabilityRange(element: KtCallExpression): TextRange? {
        val callee = element.calleeExpression ?: return null
        val (fromFunctionName, toFunctionName, _, _) = conversions[callee.text] ?: return null
        val fqNames = functions[fromFunctionName] ?: return null
        val lambda = element.singleLambdaArgumentExpression() ?: return null
        val lambdaBody = lambda.bodyExpression ?: return null
        val lastStatement = lambdaBody.statements.lastOrNull() ?: return null
        if (lambdaBody.anyDescendantOfType<KtReturnExpression> { it != lastStatement }) return null

        val context = element.analyze(BodyResolveMode.PARTIAL)
        if (element.getResolvedCall(context)?.resultingDescriptor?.fqNameOrNull() !in fqNames) return null
        val predicate = when (lastStatement) {
            is KtReturnExpression -> {
                val targetFunctionDescriptor = lastStatement.getTargetFunctionDescriptor(context)
                val lambdaDescriptor = context[BindingContext.FUNCTION, lambda.functionLiteral]
                if (targetFunctionDescriptor == lambdaDescriptor) lastStatement.returnedExpression else null
            }
            else -> lastStatement
        } ?: return null
        if (predicate.getType(context)?.isBoolean() != true) return null

        setTextGetter(KotlinBundle.messagePointer("replace.0.with.1", fromFunctionName, toFunctionName))
        return callee.textRange
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val (_, toFunctionName, negateCall, negatePredicate) = conversions[element.calleeExpression?.text] ?: return
        val lambda = element.singleLambdaArgumentExpression() ?: return
        val lastStatement = lambda.bodyExpression?.statements?.lastOrNull() ?: return
        val returnExpression = lastStatement.safeAs<KtReturnExpression>()
        val predicate = returnExpression?.returnedExpression ?: lastStatement
        if (negatePredicate) negate(predicate)
        val psiFactory = KtPsiFactory(element.project)
        if (returnExpression?.getLabelName() == element.calleeExpression?.text) {
            returnExpression?.labelQualifier?.replace(psiFactory.createLabelQualifier(toFunctionName))
        }
        val callOrQualified = element.getQualifiedExpressionForSelectorOrThis()
        val parentNegatedExpression = callOrQualified.parentNegatedExpression()
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

    private fun negate(predicate: KtExpression) {
        val exclPrefixExpression = predicate.asExclPrefixExpression()
        if (exclPrefixExpression != null) {
            val replaced = exclPrefixExpression.baseExpression?.let { predicate.replaced(it) }
            replaced.removeUnnecessaryParentheses()
            return
        }

        val psiFactory = KtPsiFactory(predicate.project)
        val replaced = predicate.replaced(psiFactory.createExpressionByPattern("!($0)", predicate)) as KtPrefixExpression
        replaced.baseExpression.removeUnnecessaryParentheses()
        when (val baseExpression = replaced.baseExpression?.deparenthesize()) {
            is KtBinaryExpression -> {
                val operationToken = baseExpression.operationToken
                if (operationToken == KtTokens.ANDAND || operationToken == KtTokens.OROR) {
                    ConvertBinaryExpressionWithDemorgansLawIntention.Holder.convertIfPossible(baseExpression)
                } else {
                    NegatedBinaryExpressionSimplificationUtils.simplifyNegatedBinaryExpressionIfNeeded(replaced)
                }
            }

            is KtQualifiedExpression -> {
                baseExpression.invertSelectorFunction()?.let { replaced.replace(it) }
            }
        }
    }

    private fun KtExpression.parentNegatedExpression(): KtExpression? {
        val parent = parents.dropWhile { it is KtParenthesizedExpression }.firstOrNull() ?: return null
        return parent.asExclPrefixExpression() ?: parent.asQualifiedExpressionWithNotCall()
    }

    private fun PsiElement.asExclPrefixExpression(): KtPrefixExpression? {
        return safeAs<KtPrefixExpression>()?.takeIf { it.operationToken == KtTokens.EXCL && it.baseExpression != null }
    }

    private fun PsiElement.asQualifiedExpressionWithNotCall(): KtQualifiedExpression? {
        return safeAs<KtQualifiedExpression>()?.takeIf { it.callExpression?.isCalling(FqName("kotlin.Boolean.not")) == true }
    }

    private fun KtExpression?.removeUnnecessaryParentheses() {
        if (this !is KtParenthesizedExpression) return
        val innerExpression = this.expression ?: return
        if (KtPsiUtil.areParenthesesUseless(this)) {
            this.replace(innerExpression)
        }
    }

    private fun KtPsiFactory.createLabelQualifier(labelName: String): KtContainerNode {
        return (createExpression("return@$labelName 1") as KtReturnExpression).labelQualifier!!
    }
}

private val collectionFunctions: Map<String, List<FqName>> =
    listOf("all", "any", "none", "filter", "filterNot", "filterTo", "filterNotTo").associateWith {
        listOf(FqName("kotlin.collections.$it"), FqName("kotlin.sequences.$it"))
    }

private val standardFunctions: Map<String, List<FqName>> =
    listOf("takeIf", "takeUnless").associateWith {
        listOf(FqName("kotlin.$it"))
    }

private val functions: Map<String, List<FqName>> = collectionFunctions + standardFunctions

private data class Conversion(
    val fromFunctionName: String,
    val toFunctionName: String,
    val negateCall: Boolean,
    val negatePredicate: Boolean
)

internal class ConvertCallToOppositeIntention : ConvertFunctionWithDemorgansLawIntention(
    KotlinBundle.messagePointer("replace.function.call.with.the.opposite"),
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
)

internal class ConvertAnyToAllAndViceVersaIntention : ConvertFunctionWithDemorgansLawIntention(
    KotlinBundle.messagePointer("replace.0.with.1.and.vice.versa", "any", "all"),
    listOf(
        Conversion("any", "all", true, true),
        Conversion("all", "any", true, true)
    )
)

internal class ConvertAnyToNoneAndViceVersaIntention : ConvertFunctionWithDemorgansLawIntention(
    KotlinBundle.messagePointer("replace.0.with.1.and.vice.versa", "any", "none"),
    listOf(
        Conversion("any", "none", true, false),
        Conversion("none", "any", true, false)
    )
)
