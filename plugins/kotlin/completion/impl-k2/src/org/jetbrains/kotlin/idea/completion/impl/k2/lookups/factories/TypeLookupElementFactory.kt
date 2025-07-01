// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.TailTextProvider.getTailText
import org.jetbrains.kotlin.idea.completion.lookups.factories.insertAndShortenReferencesInStringUsingTemporarySuffix
import org.jetbrains.kotlin.idea.completion.lookups.withClassifierSymbolInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance

internal object TypeLookupElementFactory {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createLookup(type: KaType): LookupElement? {
        val renderedType = type.render(TYPE_RENDERING_OPTIONS_SHORT_NAMES, position = Variance.INVARIANT)
        val lookupObject = TypeLookupObject(type.render(TYPE_RENDERING_OPTIONS, position = Variance.INVARIANT))

        val symbol = type.getSymbolIfTypeParameterOrUsualClass()

        val element = LookupElementBuilder.create(lookupObject, renderedType)
            .withInsertHandler(TypeInsertHandler)
            .let { if (symbol != null) withClassifierSymbolInfo(symbol, it) else it }

        return when (type) {
            is KaTypeParameterType -> element

            is KaUsualClassType -> {
                val tailText = getTailText(type.symbol, usePackageFqName = true, addTypeParameters = false)
                element.withTailText(tailText)
            }

            is KaFunctionType -> element.withIcon(KotlinIcons.LAMBDA)

            else -> null
        }
    }

    context(KaSession)
    fun createLookup(symbol: KaClassifierSymbol): LookupElement? {
        val relativeName = symbol.name
            ?: return null

        val descriptor = when (symbol) {
            is KaClassLikeSymbol -> symbol.classId
                ?.let { (packageFqName, relativeClassName, _) ->
                    ClassDescriptor(
                        relativeClassName = relativeClassName,
                        renderedClassId = buildString { // see ClassId::asFqNameString
                            if (!packageFqName.isRoot) {
                                append(packageFqName.render())
                                append(".")
                            }
                            append(relativeClassName.render())
                        },
                        tailText = getTailText(symbol, usePackageFqName = true),
                    )
                }

            else -> null
        }

        return LookupElementBuilder.create(
            /* lookupObject = */ TypeLookupObject(descriptor?.renderedClassId ?: relativeName.render()),
            /* lookupString = */ descriptor?.relativeClassName?.asString() ?: relativeName.asString(),
        ).withInsertHandler(TypeInsertHandler)
            .let { withClassifierSymbolInfo(symbol, it) }
            .withTailText(descriptor?.tailText)
    }

    private fun KaType.getSymbolIfTypeParameterOrUsualClass(): KaClassifierSymbol? = when (this) {
        is KaTypeParameterType -> symbol
        is KaUsualClassType -> symbol
        else -> null
    }

    @KaExperimentalApi
    private val TYPE_RENDERING_OPTIONS_SHORT_NAMES = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
        annotationsRenderer = annotationsRenderer.with { annotationFilter = KaRendererAnnotationsFilter.NONE }
    }

    @KaExperimentalApi
    private val TYPE_RENDERING_OPTIONS = KaTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
        annotationsRenderer = annotationsRenderer.with { annotationFilter = KaRendererAnnotationsFilter.NONE }
    }
}

@Serializable
data class TypeLookupObject(
    val fqRenderedType: String,
): SerializableLookupObject

@Serializable
internal object TypeInsertHandler : SerializableInsertHandler {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val lookupObject = item.`object` as TypeLookupObject
        context.insertAndShortenReferencesInStringUsingTemporarySuffix(lookupObject.fqRenderedType)
    }
}

private data class ClassDescriptor(
    val relativeClassName: FqName,
    val renderedClassId: @NonNls String,
    val tailText: @NlsSafe String,
)