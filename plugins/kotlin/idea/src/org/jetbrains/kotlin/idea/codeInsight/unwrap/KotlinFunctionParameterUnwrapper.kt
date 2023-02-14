// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.unwrap

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.getExpressionShortText
import org.jetbrains.kotlin.idea.util.isComma
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.util.takeWhileIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinFunctionParameterUnwrapper(val key: String) : KotlinUnwrapRemoveBase(key) {

    override fun isApplicableTo(element: PsiElement): Boolean {
        val argumentToUnwrap = argumentToUnwrap(element) ?: return false
        deletionTarget(argumentToUnwrap) ?: return false

        return true
    }

    private fun argumentToUnwrap(element: PsiElement?): KtValueArgument? {
        if (element == null) return null

        nearbySingleArg(element)?.let {
            return it
        }
        when (element) {
            is KtValueArgument -> return element
            else -> {
                if (element.parent !is KtValueArgumentList) return null

                if (element.isComma || element.elementType == KtTokens.RPAR || element is PsiWhiteSpace || element is PsiComment) {
                    return findAdjacentValueArgumentInsideParen(element)
                } else return null
            }
        }
    }

    /**
     * When outside a function call's parenthesized arguments but the function has a single argument, choose that
     * ```
     *           ┌ LPAR
     *           │
     *           v
     * methodCall(arguments)
     * ^^^^^^^^^^
     * │
     * └ Identifier
     * ```
     *
     */
    private fun nearbySingleArg(element: PsiElement): KtValueArgument? {
        // a.b.c.d(123)
        // ^^^^^^^
        // walk up chained dot expressions until we find the one that has a call as rhs
        // we use this complicated rather than just have isApplicableTo apply to a KtQualifiedExpression to get priority over
        // "Remove" unwrap item
        fun caretOnQualifier(qualifiedExpression: KtQualifiedExpression): KtCallExpression? =
            qualifiedExpression.parentsWithSelf.takeWhileIsInstance<KtQualifiedExpression>().last().selectorExpression as? KtCallExpression

        val callExpression: KtCallExpression = when (element.elementType) {
            KtTokens.LPAR -> {
                element.parent?.safeAs<KtValueArgumentList>()
                    ?.parent?.safeAs<KtCallExpression>() ?: return null
            }
            KtTokens.DOT, KtTokens.SAFE_ACCESS -> {
                element.parent?.safeAs<KtQualifiedExpression>()
                    ?.let { caretOnQualifier(it) } ?: return null
            }
            KtTokens.IDENTIFIER -> {
                val referenceExpression = element.parent?.safeAs<KtReferenceExpression>()
                when (val parent = referenceExpression?.parent) {
                    is KtCallExpression -> parent
                    is KtQualifiedExpression -> {
                        caretOnQualifier(parent)
                    }
                    else -> null
                } ?: return null
                // we could just handle this on
            }
            else -> return null
        }

        val ktValueArgumentList = callExpression.valueArgumentList
        if (ktValueArgumentList?.arguments.isNullOrEmpty()) {
            // if there's no parenthesized arguments, unwrap trailing lambda if any
            return callExpression.lambdaArguments.singleOrNull()
        }

        // there are parenthesized arguments, only consider if there's exactly one
        return ktValueArgumentList?.arguments?.singleOrNull()
    }

    /**
     * When inside a function call's parenthesized arguments.
     *
     * * for Comma and RPAR, we scan towards the left
     * * for whitespace, we scan leftwards and rightwards
     *
     * ```
     *                 ┌ COMMA
     *                 │   ┌ RPAR
     *                 │   │
     *                 v   v
     * methodCall(1, 2 , 3 )
     *              ^   ^ ^
     *              │   │ │
     *   Whitespace ┴───┴─┘
     * ```
     *
     */
    private fun findAdjacentValueArgumentInsideParen(element: PsiElement): KtValueArgument? {
        if (element.parent !is KtValueArgumentList) {
            return null
        }

        return when {
            element.elementType == KtTokens.RPAR -> {
                // caret after last argument before closing paren, argument is the last element
                val valueArgumentList = element.parent as? KtValueArgumentList ?: return null
                if (valueArgumentList.parent !is KtCallExpression) return null
                return valueArgumentList.arguments.lastOrNull()
            }
            element.isComma -> {
                // caret before comma, choose previous argument
                val previous = element.getPrevSiblingIgnoringWhitespaceAndComments()
                isCallArgument(previous).ifTrue { previous as KtValueArgument }
            }
            element is PsiWhiteSpace || element is PsiComment -> {
                // caret before blank or in comment, argument could be towards the left or the right
                val previous = element.getPrevSiblingIgnoringWhitespaceAndComments()
                if (isCallArgument(previous)) return previous as KtValueArgument

                val next = element.getNextSiblingIgnoringWhitespaceAndComments()
                if (isCallArgument(next)) return next as KtValueArgument

                null
            }
            else -> null
        }
    }

    private fun isCallArgument(element: PsiElement?): Boolean {
        if (element is KtLambdaArgument) return false
        if (element !is KtValueArgument) return false
        val argumentList = element.parent as KtValueArgumentList
        if (argumentList.parent !is KtCallExpression) return false

        return true
    }

    override fun doUnwrap(element: PsiElement?, context: Context?) {
        val valueArgument = argumentToUnwrap(element) ?: return
        val deletionTarget = deletionTarget(valueArgument) ?: return
        val argument = valueArgument.getArgumentExpression() ?: return
        context?.extractFromExpression(argument, deletionTarget)
        context?.delete(deletionTarget)
    }

    private fun deletionTarget(valueArgument: KtValueArgument): KtElement? {
        var function: KtElement = valueArgument.getStrictParentOfType<KtCallExpression>() ?: return null
        val parent = function.parent
        if (parent is KtQualifiedExpression) {
            function = parent
        }
        return function
    }

    override fun collectAffectedElements(e: PsiElement, toExtract: MutableList<in PsiElement>): PsiElement {
        super.collectAffectedElements(e, toExtract)
        return argumentToUnwrap(e)?.let { deletionTarget(it) } ?: e
    }

    override fun getDescription(e: PsiElement): String {
        // necessary because the base implementation expects to be called for KtElement
        // but because we support the caret to be on LPAR/COMMA and other tokens this doesn't work

        val target = argumentToUnwrap(e) ?: error("Description asked for a non applicable unwrapper")
        val callee = target.getStrictParentOfType<KtCallExpression>()?.calleeExpression
            ?.let(::getExpressionShortText) ?: "?"

        return KotlinBundle.message(key, callee, getExpressionShortText(target))
    }
}
