// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeInDependedAnalysisSession
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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.allChildren

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

@OptIn(KtAllowAnalysisOnEdt::class)
fun shortenReferencesInRange(
    elementToReanalyze: KtElement,
    range: TextRange = elementToReanalyze.containingFile.originalFile.textRange,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KtClassLikeSymbol) -> ShortenStrategy = defaultClassShortenStrategy,
    callableShortenStrategy: (KtCallableSymbol) -> ShortenStrategy = defaultCallableShortenStrategy
): PsiElement? {
    val ktFile = elementToReanalyze.containingFile as KtFile
    val originalFile = ktFile.originalFile as KtFile
    val shortenCommand =
        if (!elementToReanalyze.isPhysical && originalFile.isPhysical) {
            analyzeInDependedAnalysisSession(originalFile, elementToReanalyze) {
                collectPossibleReferenceShortenings(elementToReanalyze.containingKtFile, range, shortenOptions, classShortenStrategy, callableShortenStrategy)
            }
        } else {
            allowAnalysisOnEdt {
                @OptIn(KtAllowAnalysisFromWriteAction::class)
                allowAnalysisFromWriteAction {
                    analyze(ktFile) {
                        collectPossibleReferenceShortenings(ktFile, range, shortenOptions, classShortenStrategy, callableShortenStrategy)
                    }
                }
            }
        }

    return shortenCommand.invokeShortening().firstOrNull()
}

/**
 * Shortens the references specified in [ShortenCommand] and inserts needed imports
 */
fun ShortenCommand.invokeShortening(): List<KtElement> {
    // if the file has been invalidated, there's nothing we can shorten
    val targetFile = targetFile.element ?: return emptyList()

    for (nameToImport in importsToAdd) {
        targetFile.addImport(nameToImport)
    }

    for (nameToImport in starImportsToAdd) {
        targetFile.addImport(nameToImport, allUnder = true)
    }

    val shorteningResults = mutableListOf<KtElement>()
    //todo
    //        PostprocessReformattingAspect.getInstance(targetFile.project).disablePostprocessFormattingInside {
    for (typePointer in typesToShorten) {
        val type = typePointer.element ?: continue
        type.deleteQualifier()
        shorteningResults.add(type)
    }

    for (callPointer in qualifiersToShorten) {
        val call = callPointer.element ?: continue
        call.deleteQualifier()?.let { shorteningResults.add(it) }
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