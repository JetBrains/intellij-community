// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.components.collectPossibleReferenceShortenings
import org.jetbrains.kotlin.analysis.api.components.collectPossibleReferenceShorteningsInElement
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenOptionsForIde
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
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
    shortenOptions: ShortenOptionsForIde = ShortenOptionsForIde.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategyForIde(element),
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategyForIde(element),
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
    shortenOptions: ShortenOptionsForIde = ShortenOptionsForIde.DEFAULT,
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
    shortenOptions: ShortenOptionsForIde = ShortenOptionsForIde.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategyForIde(elements.firstOrNull()),
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategyForIde(elements.firstOrNull()),
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
 * modify PSI on the EDT thread by invoking [invokeShortening] on the resulting [ShortenCommandForIde].
 */
@OptIn(
    KaAllowAnalysisFromWriteAction::class,
    KaAllowAnalysisOnEdt::class,
)
fun shortenReferencesInRange(
    file: KtFile,
    selection: TextRange = file.textRange,
    shortenOptions: ShortenOptionsForIde = ShortenOptionsForIde.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategyForIde(file),
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategyForIde(file),
): List<SmartPsiElementPointer<KtElement>> = allowAnalysisFromWriteActionInEdt(file) {
    collectPossibleReferenceShorteningsForIde(file, selection, shortenOptions, classShortenStrategy, callableShortenStrategy)
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
    shortenOptions: ShortenOptionsForIde = ShortenOptionsForIde.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategy,
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategy,
): PsiElement? = shortenReferencesInRange(file, selection, shortenOptions, classShortenStrategy, callableShortenStrategy)
    .firstNotNullOfOrNull { it.element }

/**
 * Shortens the references specified in [ShortenCommandForIde] and inserts needed imports
 */
fun ShortenCommandForIde.invokeShortening(): List<SmartPsiElementPointer<KtElement>> {
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

    for ((companionReference) in companionReferencesToShorten) {
        val companionReferenceElement = companionReference.element ?: continue

        if (companionReferenceElement.getReceiverExpression() == null) {
            // if the explicit receiver of the companion was removed,
            // we cannot safely remove the companion reference anymore
            continue
        }

        companionReferenceElement.deleteReferenceFromQualifiedExpression()
        // N.B. `shorteningResults` are not updated for now
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

/**
 * Collects possible references to shorten.
 *
 * Compared to [collectPossibleReferenceShortenings], uses [defaultClassShortenStrategyForIde] and [defaultCallableShortenStrategyForIde]
 * strategies for shortening by default, which respect Kotlin Code Style Settings from the IDE.
 *
 * In the IDE, this function should be preferred to [collectPossibleReferenceShortenings] due to better defaults.
 *
 * Overall, consider using more simple and straighforward [shortenReferences] functions,
 * or [org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility] if you need to support both K1 and K2 Modes.
 */
context(_: KaSession)
@ApiStatus.Internal
fun collectPossibleReferenceShorteningsForIde(
    file: KtFile,
    selection: TextRange = file.textRange,
    shortenOptions: ShortenOptionsForIde = ShortenOptionsForIde.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategyForIde(file),
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategyForIde(file),
): ShortenCommandForIde {
    val shortenCommand = collectPossibleReferenceShortenings(
        file,
        selection,
        shortenOptions.toShortenOptions(),
        classShortenStrategy,
        callableShortenStrategy
    )

    val companionReferencesToShorten = collectPossibleCompanionReferenceShortenings(file, selection, shortenOptions)
        .map { CompanionReferenceToShorten(it.createSmartPointer()) }

    return ShortenCommandForIdeImpl(shortenCommand, companionReferencesToShorten)
}

/**
 * Collects possible references to shorten.
 *
 * Compared to [collectPossibleReferenceShorteningsInElement], uses [defaultClassShortenStrategyForIde] and [defaultCallableShortenStrategyForIde]
 * strategies for shortening by default, which respect Kotlin Code Style Settings from the IDE.
 *
 * In the IDE, this function should be preferred to [collectPossibleReferenceShorteningsInElement] due to better defaults.
 *
 * Overall, consider using more simple and straighforward [shortenReferences] functions,
 * or [org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility] if you need to support both K1 and K2 Modes.
 */
context(_: KaSession)
@ApiStatus.Internal
fun collectPossibleReferenceShorteningsInElementForIde(
    element: KtElement,
    shortenOptions: ShortenOptionsForIde = ShortenOptionsForIde.DEFAULT,
    classShortenStrategy: (KaClassLikeSymbol) -> ShortenStrategy = ShortenStrategy.defaultClassShortenStrategyForIde(element),
    callableShortenStrategy: (KaCallableSymbol) -> ShortenStrategy = ShortenStrategy.defaultCallableShortenStrategyForIde(element),
): ShortenCommandForIde {
    val shortenCommand = collectPossibleReferenceShorteningsInElement(
        element,
        shortenOptions.toShortenOptions(),
        classShortenStrategy,
        callableShortenStrategy,
    )

    val companionReferencesToShorten = collectPossibleCompanionReferenceShorteningsInElement(element, shortenOptions)
        .map { CompanionReferenceToShorten(it.createSmartPointer()) }

    return ShortenCommandForIdeImpl(shortenCommand, companionReferencesToShorten)
}


/**
 * Mostly a copy of [ShortenStrategy.defaultClassShortenStrategy] which also respects Kotlin Code Style Settings from the IDE
 * applied at the [context] position.
 */
@ApiStatus.Internal
fun ShortenStrategy.Companion.defaultClassShortenStrategyForIde(context: KtElement?): (KaClassLikeSymbol) -> ShortenStrategy {
    if (context == null) return defaultClassShortenStrategy

    val codeStyleSettings = context.containingKtFile.kotlinCustomSettings
    val importNestedClasses = codeStyleSettings.IMPORT_NESTED_CLASSES

    return { classLikeSymbol ->
        if (classLikeSymbol.classId?.isNestedClass == true && !importNestedClasses) {
            ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED
        } else {
            ShortenStrategy.SHORTEN_AND_IMPORT
        }
    }
}

/**
 * Mostly a copy of [ShortenStrategy.defaultCallableShortenStrategy] which also respects Kotlin Code Style Settings from the IDE
 * applied at the [context] position.
 */
@ApiStatus.Internal
fun ShortenStrategy.Companion.defaultCallableShortenStrategyForIde(context: KtElement?): (KaCallableSymbol) -> ShortenStrategy {
    if (context == null) return defaultCallableShortenStrategy

    val codeStyleSettings = context.containingKtFile.kotlinCustomSettings
    val importNestedClasses = codeStyleSettings.IMPORT_NESTED_CLASSES

    return { callableSymbol ->
        when (callableSymbol) {
            is KaEnumEntrySymbol -> ShortenStrategy.DO_NOT_SHORTEN

            is KaConstructorSymbol -> {
                val isNestedClassConstructor = callableSymbol.containingClassId?.isNestedClass == true

                if (isNestedClassConstructor && !importNestedClasses) {
                    ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED
                } else {
                    ShortenStrategy.SHORTEN_AND_IMPORT
                }
            }

            else -> {
                val isNotTopLevel = callableSymbol.callableId?.classId != null

                if (isNotTopLevel) {
                    ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED
                } else {
                    ShortenStrategy.SHORTEN_AND_IMPORT
                }
            }
        }
    }
}
