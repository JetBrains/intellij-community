// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch

import com.intellij.psi.PsiComment
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtClassTypeQualifierRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
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

context(KtAnalysisSession)
internal fun KtType.renderNames(): Array<String> = arrayOf(
    render(KtTypeRendererForSource.WITH_SHORT_NAMES.with {
        classIdRenderer = KtClassTypeQualifierRenderer.WITH_SHORT_NAMES
    }, Variance.INVARIANT),
    render(KtTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT),
    render(KtTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)
)

internal fun String.removeTypeParameters(): String {
    if (!contains('<') || !contains('>')) return this
    return removeRange(indexOfFirst { c -> c == '<' }, indexOfLast { c -> c == '>' } + 1)
}

internal val MatchingHandler.withinHierarchyTextFilterSet: Boolean
    get() = this is SubstitutionHandler && (this.isSubtype || this.isStrictSubtype)

context(KtAnalysisSession)
fun KtExpression.findDispatchReceiver(): KtType? {
    val symbol = resolveCall()?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol ?: return null
    val containingClass = symbol.getContainingSymbol() as? KtClassOrObjectSymbol ?: return null
    val classId = containingClass.classIdIfNonLocal ?: return null
    val fromKotlinPkg = classId.packageFqName.asString().startsWith("kotlin")
    val isFunctionCall = classId.relativeClassName.asString().startsWith("Function")
    if (fromKotlinPkg && isFunctionCall) return null // if function is function local return null
    return buildClassType(containingClass)
}

internal val KtQualifiedExpression.calleeName: String? get() = ((selectorExpression as? KtCallExpression)?.calleeExpression as? KtNameReferenceExpression)?.text