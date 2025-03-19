// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.*
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertString
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.TypeTextProvider.getTypeTextForCallable
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.TypeTextProvider.getTypeTextForClassifier
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionCallLookupObject
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtTypeReference

context(KaSession)
internal fun withClassifierSymbolInfo(
    symbol: KaClassifierSymbol,
    elementBuilder: LookupElementBuilder
): LookupElementBuilder = elementBuilder
    .withPsiElement(symbol.psi) // TODO check if it is a heavy operation and should be postponed
    .withIcon(getIconFor(symbol))
    .withTypeText(getTypeTextForClassifier(symbol))
    .withStrikeoutness(symbol.requireStrikeoutness())

context(KaSession)
internal fun withCallableSignatureInfo(
    signature: KaCallableSignature<*>,
    elementBuilder: LookupElementBuilder
): LookupElementBuilder = elementBuilder
    .withPsiElement(signature.symbol.psi)
    .withIcon(getIconFor(signature.symbol))
    .withTypeText(getTypeTextForCallable(signature, treatAsFunctionCall = elementBuilder.`object` is FunctionCallLookupObject))
    .withStrikeoutness(signature.symbol.requireStrikeoutness())

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun KaDeclarationSymbol.requireStrikeoutness(): Boolean = when {
    deprecationStatus != null -> true
    this is KaPropertySymbol -> getterDeprecationStatus != null && (isVal || setterDeprecationStatus != null)
    else -> false
}

// FIXME: This is a hack, we should think how we can get rid of it
@OptIn(KaAllowAnalysisOnEdt::class)
internal inline fun <T> withAllowedResolve(action: () -> T): T {
    @OptIn(KaAllowAnalysisFromWriteAction::class)
    allowAnalysisFromWriteAction {
        return allowAnalysisOnEdt(action)
    }
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
    superTypeQualifier?.let { typeReference ->
        val rangeToRemove = getSuperTypeQualifierRange(typeReference)
        context.document.deleteString(rangeToRemove.startOffset, rangeToRemove.endOffset)
    }
    val typeQualifier = "<${superClassId.asFqNameString()}>"
    context.insertString(typeQualifier, instanceReference.endOffset, moveCaretToEnd = false)
    context.commitDocument()
    shortenReferencesInRange(context.file as KtFile, TextRange(this.endOffset, this.endOffset + typeQualifier.length))
}

private fun getSuperTypeQualifierRange(typeReference: KtTypeReference): TextRange = TextRange(
    (typeReference.prevLeaf { it.elementType == KtTokens.LT } ?: typeReference).startOffset,
    (typeReference.nextLeaf { it.elementType == KtTokens.GT } ?: typeReference).endOffset
)

context(KaSession)
internal fun KaCallableSymbol.isExtensionCall(isFunctionalVariableCall: Boolean): Boolean =
    isExtension || isFunctionalVariableCall && (returnType as? KaFunctionType)?.hasReceiver == true