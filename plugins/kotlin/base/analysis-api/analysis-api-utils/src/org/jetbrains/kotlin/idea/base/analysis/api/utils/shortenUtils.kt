// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy.Companion.defaultCallableShortenStrategy
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy.Companion.defaultClassShortenStrategy
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

/**
 * Shorten references in the given [element]. See [shortenReferencesInRange] for more details.
 */
fun shortenReferences(
    element: KtElement,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KtClassLikeSymbol) -> ShortenStrategy = defaultClassShortenStrategy,
    callableShortenStrategy: (KtCallableSymbol) -> ShortenStrategy = defaultCallableShortenStrategy
): PsiElement? = shortenReferencesInRange(
    element,
    element.textRange,
    shortenOptions,
    classShortenStrategy,
    callableShortenStrategy
)


/**
 * Shortens multiple references at the same time, making sure that references that are already imported will be prioritized over references
 * that aren't imported yet, for example:
 * ```
 * package pack
 *
 * class A
 *
 * fun usage(a: other.A) {}
 *
 * fun usage(a: pack.A) {}
 * ```
 * Here `pack.A` should be shortened and processed first because it can be shortened without adding imports. It is generally prefered to use
 * this API instead of calling `shortenReferences` multiple times on individual references.
 */
fun shortenReferences(
    elements: Iterable<KtElement>,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KtClassLikeSymbol) -> ShortenStrategy = defaultClassShortenStrategy,
    callableShortenStrategy: (KtCallableSymbol) -> ShortenStrategy = defaultCallableShortenStrategy
) {
    val elementPointers = elements.map { it.createSmartPointer() }
    elementPointers.forEach { ptr ->
        shortenReferencesIfValid(
            ptr,
            shortenOptions,
            { symbol -> classShortenStrategy(symbol).coerceAtMost(ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED) },
            { symbol -> callableShortenStrategy(symbol).coerceAtMost(ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED) }
        )
    }
    elementPointers.forEach { ptr ->
        shortenReferencesIfValid(ptr, shortenOptions, classShortenStrategy, callableShortenStrategy)
    }
}

private fun shortenReferencesIfValid(
    ptr: SmartPsiElementPointer<KtElement>,
    shortenOptions: ShortenOptions,
    classShortenStrategy: (KtClassLikeSymbol) -> ShortenStrategy,
    callableShortenStrategy: (KtCallableSymbol) -> ShortenStrategy
): PsiElement? = ptr.element?.let { elem ->
    shortenReferences(elem, shortenOptions, classShortenStrategy, callableShortenStrategy)
}

/**
 * Shorten references in the given [file] and [range].
 *
 * This function must be invoked on the EDT thread because it modifies the underlying PSI. It analyzes Kotlin code
 * and hence could block the EDT thread for a long period of time. Therefore, this function should be called only to shorten references
 * in *newly generated code* by IDE actions. In other cases, please consider using
 * [org.jetbrains.kotlin.analysis.api.components.KtReferenceShortenerMixIn] in a background thread to perform the analysis and then
 * modify PSI on the EDT thread by invoking [org.jetbrains.kotlin.analysis.api.components.ShortenCommand.invokeShortening].
 */
@OptIn(KtAllowAnalysisOnEdt::class)
fun shortenReferencesInRange(
    file: KtFile,
    range: TextRange = file.textRange,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KtClassLikeSymbol) -> ShortenStrategy = defaultClassShortenStrategy,
    callableShortenStrategy: (KtCallableSymbol) -> ShortenStrategy = defaultCallableShortenStrategy
): PsiElement? {
    val shortenCommand = allowAnalysisOnEdt {
        @OptIn(KtAllowAnalysisFromWriteAction::class)
        allowAnalysisFromWriteAction {
            analyze(file) {
                collectPossibleReferenceShortenings(file, range, shortenOptions, classShortenStrategy, callableShortenStrategy)
            }
        }
    }

    return shortenCommand.invokeShortening().firstOrNull()
}

fun shortenReferencesInRange(
    element: KtElement,
    range: TextRange = element.containingFile.originalFile.textRange,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KtClassLikeSymbol) -> ShortenStrategy = defaultClassShortenStrategy,
    callableShortenStrategy: (KtCallableSymbol) -> ShortenStrategy = defaultCallableShortenStrategy
): PsiElement? {
    val ktFile = element.containingKtFile
    return shortenReferencesInRange(ktFile, range, shortenOptions, classShortenStrategy, callableShortenStrategy)
}

/**
 * Shortens the references specified in [ShortenCommand] and inserts needed imports
 */
fun ShortenCommand.invokeShortening(): List<KtElement> {
    // if the file has been invalidated, there's nothing we can shorten
    val targetFile = targetFile.element ?: return emptyList()
    val psiFactory = KtPsiFactory(targetFile.project)

    for (nameToImport in importsToAdd) {
        targetFile.addImport(nameToImport)
    }

    for (nameToImport in starImportsToAdd) {
        targetFile.addImport(nameToImport, allUnder = true)
    }

    val shorteningResults = mutableListOf<KtElement>()
    //todo
    //        PostprocessReformattingAspect.getInstance(targetFile.project).disablePostprocessFormattingInside {
    for ((typePointer, shortenedRef) in listOfTypeToShortenInfo) {
        val type = typePointer.element ?: continue
        if (shortenedRef == null) {
            type.deleteQualifier()
            shorteningResults.add(type)
        } else {
            val shorteningResult = type.replace(psiFactory.createExpression(shortenedRef)) as? KtElement ?: continue
            shorteningResults.add(shorteningResult)
        }
    }

    for ((callPointer, shortenedRef) in listOfQualifierToShortenInfo) {
        val call = callPointer.element ?: continue
        shortenedRef?.let {
            val callee = when (val selector = call.selectorExpression) {
                is KtArrayAccessExpression -> selector.arrayExpression
                else -> selector.getCalleeExpressionIfAny()
            }
            callee?.replace(psiFactory.createExpression(shortenedRef))
        }
        call.deleteQualifier()?.let { shorteningResults.add(it) }
    }

    for (labelInfo in thisLabelsToShorten) {
        val thisWithLabel = labelInfo.labelToShorten.element ?: continue
        thisWithLabel.labelQualifier?.delete()
        shorteningResults.add(thisWithLabel)
    }

    for (kDocNamePointer in kDocQualifiersToShorten) {
        val kDocName = kDocNamePointer.element ?: continue
        kDocName.deleteQualifier()
        shorteningResults.add(kDocName)
    }
    //        }
    return shorteningResults
}

private fun KtDotQualifiedExpression.deleteQualifier(): KtExpression? {
    val selectorExpression = selectorExpression ?: return null
    return this.replace(selectorExpression) as KtExpression
}

private fun KDocName.deleteQualifier() {
    val identifier = lastChild.takeIf { it.node.elementType == KtTokens.IDENTIFIER } ?: return
    allChildren.takeWhile { it != identifier }.forEach { it.delete() }
}