// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyse
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.tokens.HackToForceAllowRunningAnalyzeOnEDT
import org.jetbrains.kotlin.analysis.api.tokens.hackyAllowRunningOnEdt
import org.jetbrains.kotlin.idea.completion.KotlinFirIconProvider
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertSymbol
import org.jetbrains.kotlin.idea.util.shortenReferencesInRange
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSuperExpression

internal fun KtAnalysisSession.withSymbolInfo(
    symbol: KtSymbol,
    elementBuilder: LookupElementBuilder
): LookupElementBuilder = elementBuilder
    .withPsiElement(symbol.psi) // TODO check if it is a heavy operation and should be postponed
    .withIcon(KotlinFirIconProvider.getIconFor(symbol))


// FIXME: This is a hack, we should think how we can get rid of it
internal inline fun <T> withAllowedResolve(action: () -> T): T {
    @OptIn(HackToForceAllowRunningAnalyzeOnEDT::class)
    return hackyAllowRunningOnEdt(action)
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

internal fun shortenReferencesForFirCompletion(targetFile: KtFile, textRange: TextRange) {
    val shortenings = withAllowedResolve {
        analyse(targetFile) {
            collectPossibleReferenceShortenings(targetFile, textRange)
        }
    }
    shortenings.invokeShortening()
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

