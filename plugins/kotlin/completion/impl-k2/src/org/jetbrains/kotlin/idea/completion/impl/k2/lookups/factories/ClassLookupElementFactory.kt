// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.completion.lookups.*
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.withClassifierSymbolInfo
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.nameOrAnonymous
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.getTailText
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtContextReceiver
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.renderer.render

internal class ClassLookupElementFactory {
    context(KtAnalysisSession)
fun createLookup(
        symbol: KtClassLikeSymbol,
        importingStrategy: ImportStrategy,
    ): LookupElementBuilder {
        val name = symbol.nameOrAnonymous
        return LookupElementBuilder.create(ClassifierLookupObject(name, importingStrategy), name.asString())
            .withInsertHandler(ClassifierInsertionHandler)
            .withTailText(getTailText(symbol))
            .let { withClassifierSymbolInfo(symbol, it) }
    }
}


private data class ClassifierLookupObject(
    override val shortName: Name,
    val importingStrategy: ImportStrategy
) : KotlinLookupObject

/**
 * The simplest implementation of the insertion handler for a classifiers.
 */
private object ClassifierInsertionHandler : QuotedNamesAwareInsertionHandler() {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
        val targetFile = context.file as? KtFile ?: return
        val lookupObject = item.`object` as ClassifierLookupObject
        val importingStrategy = lookupObject.importingStrategy

        super.handleInsert(context, item)

        if (importingStrategy is ImportStrategy.InsertFqNameAndShorten) {
            val fqNameRendered = importingStrategy.fqName.render()

            val token = context.file.findElementAt(context.startOffset)

            val (temporaryPrefix, temporarySuffix) = when {
                token?.parent is KDocName -> "" to ""

                // add temporary suffix for type in the receiver type position, in order for it to be resolved and shortened correctly
                token?.isCallableDeclarationIdentifier() == true -> "" to ".f"

                // if a context receiver has no owner declaration then its type reference is not resolved and therefore cannot be shortened
                token?.isContextReceiverWithoutOwnerDeclaration() == true -> "" to ") fun"

                // if there is no reference in the current context and the position is not receiver type position,
                // add temporary prefix and suffix
                token?.parent !is KtNameReferenceExpression -> "$;val v:" to "$"

                caretInTheMiddleOfElement(context) -> "" to ".f"

                else -> "" to ""
            }

            context.document.replaceString(context.startOffset, context.tailOffset, temporaryPrefix + fqNameRendered + temporarySuffix)
            context.commitDocument()

            val fqNameStartOffset = context.startOffset + temporaryPrefix.length
            val fqNameEndOffset = fqNameStartOffset + fqNameRendered.length

            val rangeMarker = context.document.createRangeMarker(context.startOffset, fqNameEndOffset + temporarySuffix.length)
            val fqNameRangeMarker = context.document.createRangeMarker(fqNameStartOffset, fqNameEndOffset)

            shortenReferencesInRange(targetFile, TextRange(fqNameStartOffset, fqNameEndOffset))
            context.commitDocument()
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)

            if (rangeMarker.isValid && fqNameRangeMarker.isValid) {
                context.document.deleteString(rangeMarker.startOffset, fqNameRangeMarker.startOffset)
                context.document.deleteString(fqNameRangeMarker.endOffset, rangeMarker.endOffset)
            }
        } else if (importingStrategy is ImportStrategy.AddImport) {
            addImportIfRequired(targetFile, importingStrategy.nameToImport)
        }
    }

    private fun caretInTheMiddleOfElement(context: InsertionContext): Boolean {
        val caretOffset = context.editor.caretModel.offset
        val element = context.file.findElementAt(caretOffset) ?: return false
        return element.startOffset < caretOffset && caretOffset < element.endOffset
    }

    private fun PsiElement.isCallableDeclarationIdentifier(): Boolean =
        elementType == KtTokens.IDENTIFIER && parent is KtCallableDeclaration

    private fun PsiElement.isContextReceiverWithoutOwnerDeclaration(): Boolean {
        val contextReceiver = parentOfType<KtContextReceiver>()
        val contextReceiverList = contextReceiver?.parent as? KtContextReceiverList ?: return false

        return contextReceiverList.parent !is KtDeclaration
    }
}
