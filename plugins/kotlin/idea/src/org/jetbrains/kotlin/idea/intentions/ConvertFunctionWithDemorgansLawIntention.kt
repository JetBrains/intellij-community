// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.inspections.ReplaceNegatedIsEmptyWithIsNotEmptyInspection.Companion.invertSelectorFunction
import org.jetbrains.kotlin.idea.inspections.SimplifyNegatedBinaryExpressionInspection
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

sealed class ConvertFunctionWithDemorgansLawIntention(
    intentionName: () -> String,
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
        val lambdaBody = element.singleLambdaArgumentExpression()?.bodyExpression ?: return null
        if (lambdaBody.anyDescendantOfType<KtReturnExpression>()) return null

        val context = element.analyze(BodyResolveMode.PARTIAL)
        if (element.getResolvedCall(context)?.resultingDescriptor?.fqNameOrNull() !in fqNames) return null
        if (lambdaBody.statements.lastOrNull()?.getType(context)?.isBoolean() != true) return null

        setTextGetter(KotlinBundle.lazyMessage("replace.0.with.1", fromFunctionName, toFunctionName))
        return callee.textRange
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val (_, toFunctionName, negateCall, negatePredicate) = conversions[element.calleeExpression?.text] ?: return
        val lambda = element.singleLambdaArgumentExpression() ?: return
        val predicate = lambda.bodyExpression?.statements?.lastOrNull() ?: return
        if (negatePredicate) negate(predicate)
        val callOrQualified = element.getQualifiedExpressionForSelectorOrThis()
        val parentNegatedExpression = callOrQualified.parentNegatedExpression()
        KtPsiFactory(element).buildExpression {
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

        val replaced = predicate.replaced(KtPsiFactory(predicate).createExpressionByPattern("!($0)", predicate)) as KtPrefixExpression
        replaced.baseExpression.removeUnnecessaryParentheses()
        when (val baseExpression = replaced.baseExpression?.deparenthesize()) {
            is KtBinaryExpression -> {
                val operationToken = baseExpression.operationToken
                if (operationToken == KtTokens.ANDAND || operationToken == KtTokens.OROR) {
                    ConvertBinaryExpressionWithDemorgansLawIntention.convertIfPossible(baseExpression)
                } else {
                    SimplifyNegatedBinaryExpressionInspection.simplifyNegatedBinaryExpressionIfNeeded(replaced)
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

    companion object {
        private val collectionFunctions = listOf("all", "any", "none", "filter", "filterNot", "filterTo", "filterNotTo").associateWith {
            listOf(FqName("kotlin.collections.$it"), FqName("kotlin.sequences.$it"))
        }

        private val standardFunctions = listOf("takeIf", "takeUnless").associateWith {
            listOf(FqName("kotlin.$it"))
        }

        private val functions = collectionFunctions + standardFunctions
    }
}

private data class Conversion(
    val fromFunctionName: String,
    val toFunctionName: String,
    val negateCall: Boolean,
    val negatePredicate: Boolean
)

class ConvertCallToOppositeIntention : ConvertFunctionWithDemorgansLawIntention(
    KotlinBundle.lazyMessage("replace.function.call.with.the.opposite"),
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

class ConvertAnyToAllAndViceVersaIntention : ConvertFunctionWithDemorgansLawIntention(
    KotlinBundle.lazyMessage("replace.0.with.1.and.vice.versa", "any", "all"),
    listOf(
        Conversion("any", "all", true, true),
        Conversion("all", "any", true, true)
    )
)

class ConvertAnyToNoneAndViceVersaIntention : ConvertFunctionWithDemorgansLawIntention(
    KotlinBundle.lazyMessage("replace.0.with.1.and.vice.versa", "any", "none"),
    listOf(
        Conversion("any", "none", true, false),
        Conversion("none", "any", true, false)
    )
)
