// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.getTailText
import org.jetbrains.kotlin.idea.completion.lookups.factories.insertAndShortenReferencesInStringUsingTemporarySuffix
import org.jetbrains.kotlin.idea.completion.lookups.withClassifierSymbolInfo
import org.jetbrains.kotlin.types.Variance

class TypeLookupElementFactory {
    context(KaSession)
    fun createLookup(type: KtType): LookupElement? {
        val renderedType = type.render(TYPE_RENDERING_OPTIONS_SHORT_NAMES, position = Variance.INVARIANT)
        val lookupObject = TypeLookupObject(type.render(TYPE_RENDERING_OPTIONS, position = Variance.INVARIANT))

        val symbol = type.getSymbolIfTypeParameterOrUsualClass()

        val element = LookupElementBuilder.create(lookupObject, renderedType)
            .withInsertHandler(TypeInsertHandler)
            .let { if (symbol != null) withClassifierSymbolInfo(symbol, it) else it }

        return when (type) {
            is KtTypeParameterType -> element

            is KtUsualClassType -> {
                val tailText = getTailText(type.symbol, usePackageFqName = true, addTypeParameters = false)
                element.withTailText(tailText)
            }

            is KtFunctionalType -> element.withIcon(KotlinIcons.LAMBDA)

            else -> null
        }
    }

    context(KaSession)
    fun createLookup(symbol: KaClassifierSymbol): LookupElement? {
        val (relativeNameAsString, fqNameAsString) = when (symbol) {
            is KaTypeParameterSymbol -> symbol.name.asString().let { it to it }

            is KaClassLikeSymbol -> when (val classId = symbol.classId) {
                null -> symbol.name?.asString()?.let { it to it }
                else -> classId.relativeClassName.asString() to classId.asFqNameString()
            }
        } ?: return null

        val tailText = (symbol as? KaClassLikeSymbol)?.let { getTailText(symbol, usePackageFqName = true) }

        return LookupElementBuilder.create(TypeLookupObject(fqNameAsString), relativeNameAsString)
            .withInsertHandler(TypeInsertHandler)
            .let { withClassifierSymbolInfo(symbol, it) }
            .withTailText(tailText)
    }

    private fun KtType.getSymbolIfTypeParameterOrUsualClass(): KaClassifierSymbol? = when (this) {
        is KtTypeParameterType -> symbol
        is KtUsualClassType -> symbol
        else -> null
    }

    private val TYPE_RENDERING_OPTIONS_SHORT_NAMES = KtTypeRendererForSource.WITH_SHORT_NAMES.with {
        annotationsRenderer = annotationsRenderer.with { annotationFilter = KaRendererAnnotationsFilter.NONE }
    }

    private val TYPE_RENDERING_OPTIONS = KtTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
        annotationsRenderer = annotationsRenderer.with { annotationFilter = KaRendererAnnotationsFilter.NONE }
    }
}

data class TypeLookupObject(val fqRenderedType: String)

private object TypeInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val lookupObject = item.`object` as TypeLookupObject
        context.insertAndShortenReferencesInStringUsingTemporarySuffix(lookupObject.fqRenderedType)
    }
}