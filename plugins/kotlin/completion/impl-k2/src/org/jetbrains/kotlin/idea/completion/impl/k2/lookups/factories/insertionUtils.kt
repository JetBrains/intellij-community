// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.QualifierToShortenInfo
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ThisLabelToShortenInfo
import org.jetbrains.kotlin.analysis.api.components.TypeToShortenInfo
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.completion.api.CompletionDummyIdentifierProviderService
import org.jetbrains.kotlin.idea.completion.doPostponedOperationsAndUnblockDocument
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.FqName
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
internal fun InsertionContext.insertAndShortenReferencesInStringUsingTemporarySuffix(
    string: String,
    shortenCommand: ShortenCommand? = null,
) {
    val file = file as? KtFile
        ?: return
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

        caretInTheMiddleOfElement() -> " "

        else -> ""
    }

    document.replaceString(startOffset, tailOffset, string + temporarySuffix)
    commitDocument()

    val fqNameEndOffset = startOffset + string.length
    val rangeMarker = document.createRangeMarker(startOffset, fqNameEndOffset + temporarySuffix.length)
    val fqNameRangeMarker = document.createRangeMarker(startOffset, fqNameEndOffset)

    if (shortenCommand != null
        && editor.caretModel.caretCount == 1
    ) {
        ShortenCommandWrapper(
            delegate = shortenCommand,
            copy = file,
        )
    } else {
        allowAnalysisFromWriteActionInEdt(file) {
            collectPossibleReferenceShortenings(
                file = file,
                selection = TextRange(startOffset, fqNameEndOffset),
            )
        }
    }.invokeShortening()

    commitDocument()
    doPostponedOperationsAndUnblockDocument()

    if (temporarySuffix.isNotEmpty() && rangeMarker.isValid && fqNameRangeMarker.isValid) {
        document.deleteString(fqNameRangeMarker.endOffset, rangeMarker.endOffset)
    }
}

private fun InsertionContext.caretInTheMiddleOfElement(): Boolean {
    val caretOffset = editor.caretModel.offset
    val textRange = file.findElementAt(caretOffset)
        ?.textRange
        ?: return false

    return textRange.startOffset < caretOffset // todo contains?
            && caretOffset < textRange.endOffset
}

private fun PsiElement.isContextReceiverWithoutOwnerDeclaration(): Boolean {
    val contextReceiver = parentOfType<KtContextReceiver>()
    val contextReceiverList = contextReceiver?.parent as? KtContextReceiverList
        ?: return false

    val modifierList = contextReceiverList.parent
    if (modifierList is KtDeclarationModifierList) {
        // dangling modifier list
        return false
    }
    return when (modifierList?.parent) {
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

@OptIn(KaImplementationDetail::class)
private class ShortenCommandWrapper(
    delegate: ShortenCommand,
    private val copy: KtFile,
) : ShortenCommand {

    override val targetFile: SmartPsiElementPointer<KtFile> =
        copy.createSmartPointer()

    override val importsToAdd: Set<FqName> =
        delegate.importsToAdd

    override val starImportsToAdd: Set<FqName> =
        delegate.starImportsToAdd

    override val listOfTypeToShortenInfo: List<TypeToShortenInfo> =
        delegate.listOfTypeToShortenInfo
            .mapNotNull { (typeToShorten, shortenedReference) ->
                typeToShorten.findSameElementInCopy()
                    ?.let { TypeToShortenInfo(it, shortenedReference) }
            }

    override val listOfQualifierToShortenInfo: List<QualifierToShortenInfo> =
        delegate.listOfQualifierToShortenInfo
            .mapNotNull { (qualifierToShorten, shortenedReference) ->
                qualifierToShorten.findSameElementInCopy()
                    ?.let { QualifierToShortenInfo(it, shortenedReference) }
            }

    override val thisLabelsToShorten: List<ThisLabelToShortenInfo> =
        delegate.thisLabelsToShorten
            .mapNotNull { (labelToShorten) ->
                labelToShorten.findSameElementInCopy()
                    ?.let { ThisLabelToShortenInfo(it) }
            }

    override val kDocQualifiersToShorten: List<SmartPsiElementPointer<KDocName>> =
        delegate.kDocQualifiersToShorten
            .mapNotNull { it.findSameElementInCopy() }

    private fun <T : KtElement> T.findSameElementInCopy(): T? = try {
        PsiTreeUtil.findSameElementInCopy(this, copy)
    } catch (e: IllegalStateException) {
        logger<ShortenCommandWrapper>().error(e)
        null
    }

    private fun <T : KtElement> SmartPsiElementPointer<T>.findSameElementInCopy(): SmartPsiElementPointer<T>? =
        element?.findSameElementInCopy()
            ?.createSmartPointer()
}
