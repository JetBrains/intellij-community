// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

/**
 * Shorten references in the given [element]. See [shortenReferencesInRange] for more details.
 */
@OptIn(
    KaAllowAnalysisFromWriteAction::class,
    KaAllowAnalysisOnEdt::class,
)
fun shortenReferences(
    element: KtElement,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategy,
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategy,
): KtElement? = shortenReferencesInRange(
    file = element.containingKtFile,
    selection = element.textRange,
    shortenOptions = shortenOptions,
    classShortenStrategy = classShortenStrategy,
    callableShortenStrategy = callableShortenStrategy,
).firstNotNullOfOrNull { it.element }

@Suppress("unused")
@JvmName("shortenReferences")
@Deprecated(
    message = "Use shortenReferences instead",
    replaceWith = ReplaceWith("shortenReferences(element, shortenOptions, classShortenStrategy, callableShortenStrategy)"),
    level = DeprecationLevel.HIDDEN,
)
fun deprecatedShortenReferences(
    element: KtElement,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategy,
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategy,
): PsiElement? = shortenReferences(element, shortenOptions, classShortenStrategy, callableShortenStrategy)

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
@OptIn(
    KaAllowAnalysisFromWriteAction::class,
    KaAllowAnalysisOnEdt::class,
)
fun shortenReferences(
    elements: Iterable<KtElement>,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategy,
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategy,
) {
    val elementPointers = elements.map { it.createSmartPointer() }

    fun shortenReferences(
        function: (ShortenStrategy) -> ShortenStrategy,
    ) {
        elementPointers.asSequence()
            .mapNotNull { it.element }
            .forEach { element ->
                shortenReferences(
                    element = element,
                    shortenOptions = shortenOptions,
                    classShortenStrategy = classShortenStrategy.andThen(function),
                    callableShortenStrategy = callableShortenStrategy.andThen(function),
                )
            }
    }

    shortenReferences { it.coerceAtMost(maximumValue = ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED) }
    shortenReferences { it }
}

/**
 * Shorten references in the given [file] and [selection].
 *
 * This function must be invoked on the EDT thread because it modifies the underlying PSI. It analyzes Kotlin code
 * and hence could block the EDT thread for a long period of time. Therefore, this function should be called only to shorten references
 * in *newly generated code* by IDE actions. In other cases, please consider using
 * [org.jetbrains.kotlin.analysis.api.components.KtReferenceShortenerMixIn] in a background thread to perform the analysis and then
 * modify PSI on the EDT thread by invoking [org.jetbrains.kotlin.analysis.api.components.ShortenCommand.invokeShortening].
 */
@OptIn(
    KaAllowAnalysisFromWriteAction::class,
    KaAllowAnalysisOnEdt::class,
)
fun shortenReferencesInRange(
    file: KtFile,
    selection: TextRange = file.textRange,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategy,
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategy,
): List<SmartPsiElementPointer<KtElement>> = allowAnalysisFromWriteActionInEdt(file) {
    collectPossibleReferenceShortenings(file, selection, shortenOptions, classShortenStrategy, callableShortenStrategy)
}.invokeShortening()

@Suppress("unused")
@JvmName("shortenReferencesInRange")
@Deprecated(
    message = "Use shortenReferencesInRange instead",
    replaceWith = ReplaceWith("shortenReferencesInRange(file, selection, shortenOptions, classShortenStrategy, callableShortenStrategy)"),
    level = DeprecationLevel.HIDDEN,
)
fun deprecatedShortenReferencesInRange(
    file: KtFile,
    selection: TextRange = file.textRange,
    shortenOptions: ShortenOptions = ShortenOptions.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategy,
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategy,
): PsiElement? = shortenReferencesInRange(file, selection, shortenOptions, classShortenStrategy, callableShortenStrategy)
    .firstNotNullOfOrNull { it.element }

/**
 * Shortens the references specified in [ShortenCommand] and inserts needed imports
 */
fun ShortenCommand.invokeShortening(): List<SmartPsiElementPointer<KtElement>> {
    // if the file has been invalidated, there's nothing we can shorten
    val targetFile = targetFile.element ?: return emptyList()
    val psiFactory = KtPsiFactory(targetFile.project)

    for (fqName in importsToAdd) {
        targetFile.addImport(fqName)
    }

    for (fqName in starImportsToAdd) {
        targetFile.addImport(fqName, allUnder = true)
    }

    val shorteningResults = mutableListOf<SmartPsiElementPointer<KtElement>>()
    //todo
    //        PostprocessReformattingAspect.getInstance(targetFile.project).disablePostprocessFormattingInside {
    for ((typePointer, shortenedRef) in listOfTypeToShortenInfo) {
        val type = typePointer.element ?: continue

        type.deleteQualifier()
        if (shortenedRef != null) {
            type.referenceExpression?.replace(psiFactory.createExpression(shortenedRef))
        }
        shorteningResults += type.createSmartPointer()
    }

    for ((qualifierToShorten, shortenedReference) in listOfQualifierToShortenInfo) {
        val call = qualifierToShorten.element ?: continue

        if (shortenedReference != null) {
            when (val selector = call.selectorExpression) {
                is KtArrayAccessExpression -> selector.arrayExpression
                else -> selector.getCalleeExpressionIfAny()
            }?.replace(psiFactory.createExpression(shortenedReference))
        }

        val selectorExpression = call.selectorExpression ?: continue
        val expression = call.replace(selectorExpression) as KtExpression
        shorteningResults += expression.createSmartPointer()
    }

    for ((labelToShorten) in thisLabelsToShorten) {
        val thisWithLabel = labelToShorten.element ?: continue

        thisWithLabel.labelQualifier?.delete()
        shorteningResults += thisWithLabel.createSmartPointer()
    }

    for (kDocNamePointer in kDocQualifiersToShorten) {
        val kDocName = kDocNamePointer.element ?: continue

        kDocName.deleteQualifier()
        shorteningResults += kDocName.createSmartPointer()
    }

    return shorteningResults
}

private fun KDocName.deleteQualifier() {
    val identifier = lastChild.takeIf { it.node.elementType == KtTokens.IDENTIFIER } ?: return
    allChildren.takeWhile { it != identifier }.forEach { it.delete() }
}

private inline infix fun <A, B, C> ((A) -> B).andThen(
    crossinline function: (B) -> C,
): (A) -> C = { function(this(it)) }