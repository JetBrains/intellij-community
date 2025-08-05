// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch

import com.intellij.psi.PsiComment
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaClassTypeQualifierRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.types.Variance

internal fun getCommentText(comment: PsiComment): String {
    return when (comment.tokenType) {
        KtTokens.EOL_COMMENT -> comment.text.drop(2).trim()
        KtTokens.BLOCK_COMMENT -> comment.text.drop(2).dropLast(2).trim()
        else -> ""
    }
}

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
internal fun KaType.renderNames(): Array<String> = arrayOf(
    render(KaTypeRendererForSource.WITH_SHORT_NAMES.with {
        classIdRenderer = KaClassTypeQualifierRenderer.WITH_SHORT_NAMES
    }, Variance.INVARIANT),
    render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT),
    render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)
)

internal fun String.removeTypeParameters(): String {
    if (!contains('<') || !contains('>')) return this
    return removeRange(indexOfFirst { c -> c == '<' }, indexOfLast { c -> c == '>' } + 1)
}

internal val MatchingHandler.withinHierarchyTextFilterSet: Boolean
    get() = this is SubstitutionHandler && (this.isSubtype || this.isStrictSubtype)

context(_: KaSession)
fun KtExpression.findDispatchReceiver(): KaType? {
    val symbol = resolveToCall()?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol ?: return null
    val containingClass = symbol.containingDeclaration as? KaClassSymbol ?: return null
    val classId = containingClass.classId ?: return null
    val fromKotlinPkg = classId.packageFqName.asString().startsWith("kotlin")
    val isFunctionCall = classId.relativeClassName.asString().startsWith("Function")
    if (fromKotlinPkg && isFunctionCall) return null // if function is function local return null
    return buildClassType(containingClass)
}

internal val KtQualifiedExpression.calleeName: String? get() = ((selectorExpression as? KtCallExpression)?.calleeExpression as? KtNameReferenceExpression)?.text