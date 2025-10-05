// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.fakeOverrideOriginal
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isJavaSourceOrLibrary
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
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

@ApiStatus.Internal
class ArgumentNameCommentInfo(val argumentName: Name, val comment: String) {
    @ApiStatus.Internal
    constructor(symbol: KaValueParameterSymbol): this(symbol.name, symbol.toArgumentNameComment())
}

typealias NameCommentsByArgument = Map<SmartPsiElementPointer<KtValueArgument>, ArgumentNameCommentInfo>

/**
 * Returns a map of argument name (block) comments (as [ArgumentNameCommentInfo]) that can be prepended to [element]'s arguments. The map
 * is indexed by [KtValueArgument], though the [SmartPsiElementPointer]s need to be dereferenced first. The [SmartPsiElementPointer] allows
 * the map to be stored in applicable intention contexts.
 */
context(_: KaSession)
fun getArgumentNameComments(element: KtCallElement): NameCommentsByArgument? {
    val arguments = element.getNonLambdaArguments()
    val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null

    // Use `unwrapFakeOverrides` to handle `SUBSTITUTION_OVERRIDE` and `INTERSECTION_OVERRIDE` callee symbols. Also see the test
    // `genericSuperTypeMethodCall.kt`.
    val calleeSymbol = resolvedCall.partiallyAppliedSymbol.symbol
    if (!calleeSymbol.fakeOverrideOriginal.origin.isJavaSourceOrLibrary()) return null

    return arguments
        .mapNotNull { argument ->
            val symbol = resolvedCall.argumentMapping[argument.getArgumentExpression()]?.symbol ?: return@mapNotNull null
            argument to symbol
        }
        // Take arguments until and including the first vararg parameter. A name comment should be added to the first vararg only, not
        // subsequent varargs.
        .takeWhileInclusive { !it.second.isVararg }
        .associate { (argument, symbol) ->
            argument.createSmartPointer() to ArgumentNameCommentInfo(symbol)
        }
}

private fun KtCallElement.getNonLambdaArguments(): List<KtValueArgument> =
    valueArguments.filterIsInstance<KtValueArgument>().filterNot { it is KtLambdaArgument }

private fun KaValueParameterSymbol.toArgumentNameComment(): String =
    canonicalArgumentNameComment(if (isVararg) "...$name" else name.toString())

@ApiStatus.Internal
fun PsiComment.isExpectedArgumentNameComment(info: ArgumentNameCommentInfo): Boolean {
    if (this.elementType != KtTokens.BLOCK_COMMENT) return false
    val parameterName = text
        .removePrefix("/*").removeSuffix("*/").trim()
        .takeIf { it.endsWith("=") }?.removeSuffix("=")?.trim()
        ?: return false
    return canonicalArgumentNameComment(parameterName) == info.comment
}

private fun canonicalArgumentNameComment(parameterName: String): String = "/* $parameterName = */"
