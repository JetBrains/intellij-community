// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.completion.api.CompletionDummyIdentifierProviderService
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.*

/**
 * Inserts [string] and shortens fully qualified references in it.
 *
 * Depending on the position in the file inserts [string] together with a temporary suffix, which is removed after references
 * in [string] are shortened. For example, if a classifier `dependency.Foo` is being completed in the following code:
 * ```
 * foo(n: F<caret>Int)
 * ```
 * inserts [string] together with a space at first:
 * ```
 * foo(n: dependency.Foo Int)
 * ```
 * so that the reference is resolved and shortened successfully. After the shortening is finished, removes the space.
 */
internal fun InsertionContext.insertAndShortenReferencesInStringUsingTemporarySuffix(string: String) {
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val targetFile = file as? KtFile ?: return
    val token = file.findElementAt(startOffset)

    val suffixToAffectParsingIfNecessary = token?.let {
        CompletionDummyIdentifierProviderService.getInstance().provideSuffixToAffectParsingIfNecessary(it)
    }

    val temporarySuffix = when {
        token?.parent is KDocName -> ""

        suffixToAffectParsingIfNecessary?.isNotEmpty() == true -> suffixToAffectParsingIfNecessary

        // if a context receiver has no owner declaration, then its type reference is not resolved and therefore cannot be shortened
        token?.isContextReceiverWithoutFunctionalTypeDeclaration() == true -> ") () -> Unit"

        // if a context receiver has no owner declaration, then its type reference is not resolved and therefore cannot be shortened
        token?.isContextReceiverWithoutOwnerDeclaration() == true -> ") fun"

        caretInTheMiddleOfElement(context = this) -> " "

        else -> ""
    }

    document.replaceString(startOffset, tailOffset, string + temporarySuffix)
    commitDocument()

    val fqNameEndOffset = startOffset + string.length

    val rangeMarker = document.createRangeMarker(startOffset, fqNameEndOffset + temporarySuffix.length)
    val fqNameRangeMarker = document.createRangeMarker(startOffset, fqNameEndOffset)

    shortenReferencesInRange(targetFile, TextRange(startOffset, fqNameEndOffset))
    commitDocument()
    psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)

    if (temporarySuffix.isNotEmpty() && rangeMarker.isValid && fqNameRangeMarker.isValid) {
        document.deleteString(fqNameRangeMarker.endOffset, rangeMarker.endOffset)
    }
}

private fun caretInTheMiddleOfElement(context: InsertionContext): Boolean {
    val caretOffset = context.editor.caretModel.offset
    val element = context.file.findElementAt(caretOffset) ?: return false
    return element.startOffset < caretOffset && caretOffset < element.endOffset
}

private fun PsiElement.isContextReceiverWithoutOwnerDeclaration(): Boolean {
    val contextReceiver = parentOfType<KtContextReceiver>()
    val contextReceiverList = contextReceiver?.parent as? KtContextReceiverList
        ?: return false

    return when (contextReceiverList.parent?.parent) {
        is KtDeclaration -> false
        is KtFunctionType -> false
        else -> true
    }
}

private fun PsiElement.isContextReceiverWithoutFunctionalTypeDeclaration(): Boolean {
    val contextReceiver = parentOfType<KtContextReceiver>()
    val contextReceiverList = contextReceiver?.parent as? KtContextReceiverList
        ?: return false

    return contextReceiverList.parent.let { it is KtTypeReference || it?.parent is KtTypeReference }
}