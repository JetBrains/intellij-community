// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.util.takeWhileInclusive

fun KtCallElement.hasArgumentNameComments(): Boolean = getApplicableArguments()?.any { it.hasBlockCommentWithName() } == true

fun KtCallElement.canAddArgumentNameCommentsByPsi(): Boolean = getApplicableArguments()?.any { !it.hasBlockCommentWithName() } == true

private fun KtCallElement.getApplicableArguments(): List<KtValueArgument>? =
    getNonLambdaArguments().takeIf { arguments -> arguments.isNotEmpty() && arguments.none { it.isNamed() } }

fun KtValueArgument.hasBlockCommentWithName(): Boolean = getBlockCommentWithName() != null

fun KtValueArgument.getBlockCommentWithName(): PsiComment? =
    siblings(forward = false, withSelf = false)
        .takeWhile { it is PsiWhiteSpace || it is PsiComment }
        .filterIsInstance<PsiComment>()
        .firstOrNull { it.elementType == KtTokens.BLOCK_COMMENT && it.text.removeSuffix("*/").trim().endsWith("=") }

class ArgumentNameCommentInfo(val argumentName: Name, val comment: String)

typealias NameCommentsByArgument = Map<SmartPsiElementPointer<KtValueArgument>, ArgumentNameCommentInfo>

/**
 * Returns a map of argument name (block) comments (as [ArgumentNameCommentInfo]) that can be prepended to [element]'s arguments. The map
 * is indexed by [KtValueArgument], though the [SmartPsiElementPointer]s need to be dereferenced first. The [SmartPsiElementPointer] allows
 * the map to be stored in applicable intention contexts.
 */
context(KtAnalysisSession)
fun getArgumentNameComments(element: KtCallElement): NameCommentsByArgument? {
    val arguments = element.getNonLambdaArguments()
    val resolvedCall = element.resolveCall().successfulFunctionCallOrNull() ?: return null

    // Use `unwrapFakeOverrides` to handle `SUBSTITUTION_OVERRIDE` and `INTERSECTION_OVERRIDE` callee symbols. Also see the test
    // `genericSuperTypeMethodCall.kt`.
    val calleeSymbol = resolvedCall.partiallyAppliedSymbol.symbol
    if (calleeSymbol.unwrapFakeOverrides.origin != KtSymbolOrigin.JAVA) return null

    return arguments
        .mapNotNull { argument ->
            val symbol = resolvedCall.argumentMapping[argument.getArgumentExpression()]?.symbol ?: return@mapNotNull null
            argument to symbol
        }
        // Take arguments until and including the first vararg parameter. A name comment should be added to the first vararg only, not
        // subsequent varargs.
        .takeWhileInclusive { !it.second.isVararg }
        .associate { (argument, symbol) ->
            argument.createSmartPointer() to ArgumentNameCommentInfo(symbol.name, symbol.toArgumentNameComment())
        }
}

private fun KtCallElement.getNonLambdaArguments(): List<KtValueArgument> =
    valueArguments.filterIsInstance<KtValueArgument>().filterNot { it is KtLambdaArgument }

private fun KtValueParameterSymbol.toArgumentNameComment(): String =
    canonicalArgumentNameComment(if (isVararg) "...$name" else name.toString())

fun PsiComment.isExpectedArgumentNameComment(info: ArgumentNameCommentInfo): Boolean {
    if (this.elementType != KtTokens.BLOCK_COMMENT) return false
    val parameterName = text
        .removePrefix("/*").removeSuffix("*/").trim()
        .takeIf { it.endsWith("=") }?.removeSuffix("=")?.trim()
        ?: return false
    return canonicalArgumentNameComment(parameterName) == info.comment
}

private fun canonicalArgumentNameComment(parameterName: String): String = "/* $parameterName = */"
