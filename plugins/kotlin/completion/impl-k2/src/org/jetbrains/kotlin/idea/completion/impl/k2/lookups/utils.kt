// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertSymbol
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.TypeTextProvider.getTypeText
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionCallLookupObject
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSuperExpression

internal fun KtAnalysisSession.withSymbolInfo(
    symbol: KtSymbol,
    elementBuilder: LookupElementBuilder,
    substitutor: KtSubstitutor = KtSubstitutor.Empty(token)
): LookupElementBuilder = elementBuilder
    .withPsiElement(symbol.psi) // TODO check if it is a heavy operation and should be postponed
    .withIcon(getIconFor(symbol))
    .withTypeText(getTypeText(symbol, treatAsFunctionCall = elementBuilder.`object` is FunctionCallLookupObject, substitutor))


// FIXME: This is a hack, we should think how we can get rid of it
@OptIn(KtAllowAnalysisOnEdt::class)
internal inline fun <T> withAllowedResolve(action: () -> T): T {
    return allowAnalysisOnEdt(action)
}

internal fun CharSequence.skipSpaces(index: Int): Int =
    (index until length).firstOrNull { val c = this[it]; c != ' ' && c != '\t' } ?: this.length

internal fun CharSequence.indexOfSkippingSpace(c: Char, startIndex: Int): Int? {
    for (i in startIndex until this.length) {
        val currentChar = this[i]
        if (c == currentChar) return i
        if (currentChar != ' ' && currentChar != '\t') return null
    }
    return null
}


internal fun updateLookupElementBuilderToInsertTypeQualifierOnSuper(
    builder: LookupElementBuilder,
    insertionStrategy: CallableInsertionStrategy.WithSuperDisambiguation
) =
    builder.withInsertHandler { context, item ->
        builder.insertHandler?.handleInsert(context, item)
        val superExpression = insertionStrategy.superExpressionPointer.element ?: return@withInsertHandler
        superExpression.setSuperTypeQualifier(context, insertionStrategy.superClassId)
    }.appendTailText(" for ${insertionStrategy.superClassId.relativeClassName}", true)


private fun KtSuperExpression.setSuperTypeQualifier(
    context: InsertionContext,
    superClassId: ClassId
) {
    superTypeQualifier?.delete()
    val typeQualifier = "<${superClassId.asFqNameString()}>"
    context.insertSymbol(typeQualifier, instanceReference.endOffset, moveCaretToEnd = false)
    context.commitDocument()
    shortenReferencesInRange(context.file as KtFile, TextRange(this.endOffset, this.endOffset + typeQualifier.length))
}

/**
 * Checks whether [this] is possibly a subtype of [superType] by replacing all type arguments in [superType] with star projections and
 * checking subtyping relation between [this] and obtained type. For example, `MutableList<List<T>>` is possibly a subtype of
 * `List<List<String>>`, because MutableList<List<T>>` is a subtype of `List<*>`.
 *
 * This check only approximates the possibility of the subtyping relation.
 * An accurate estimation requires the use of the constraint system, which can lead to a loss in performance.
 */
context(KtAnalysisSession)
internal infix fun KtType.isPossiblySubTypeOf(superType: KtType): Boolean {
    if (this is KtTypeParameterType) return this.hasCommonSubTypeWith(superType)

    if (superType is KtTypeParameterType) return superType.symbol.upperBounds.all { this isPossiblySubTypeOf it }

    val superTypeWithReplacedTypeArguments = superType.expandedClassSymbol?.let { symbol ->
        buildClassTypeWithStarProjections(symbol, superType.nullability)
    }
    return superTypeWithReplacedTypeArguments != null && this isSubTypeOf superTypeWithReplacedTypeArguments
}

private fun KtAnalysisSession.buildClassTypeWithStarProjections(symbol: KtClassOrObjectSymbol, nullability: KtTypeNullability): KtType =
    buildClassType(symbol) {
        repeat(symbol.typeParameters.size) {
            argument(KtStarTypeProjection(token))
        }
    }.withNullability(nullability)