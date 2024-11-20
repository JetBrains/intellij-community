// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

@KaExperimentalApi
private val NoAnnotationsTypeRenderer: KaTypeRenderer = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
    annotationsRenderer = annotationsRenderer.with {
        annotationFilter = KaRendererAnnotationsFilter.NONE
    }
}

context(KaSession)
fun createLookupElements(
    trailingFunctionType: KaFunctionType,
    suggestedParameterNames: List<Name?> = emptyList(),
): Sequence<LookupElementBuilder> {
    val typeNames = mutableMapOf<KaType, MutableSet<String>>()
    val kotlinNameSuggester = KotlinNameSuggester()

    val namedParameterTypes = trailingFunctionType.parameterTypes
        .zip(suggestedParameterNames)
    //val showDefault = namedParameterTypes.size != 1 // todo

    return namedParameterTypes.asSequence()
        .flatMapIndexed { index, (parameterType, suggestedName) ->
            //TODO: check for names in scope
            val validator = typeNames.getOrPut(parameterType) {
                mutableSetOf()
            }::add

            val suggestedNames = suggestedName?.let { sequenceOf(it.asString()) }
                ?: kotlinNameSuggester.suggestTypeNames(parameterType)

            val parameterTypeText = parameterType.text

            suggestedNames.map {
                KotlinNameSuggester.suggestNameByName(it, validator) to parameterTypeText
            }
        }.map { (name, typeText) ->
            val tailText = " -> "
            LookupElementBuilder.create(name)
                .withTailText(tailText, true)
                .withTypeText(typeText)
                .withInsertHandler { context, item ->
                    context.document.insertString(context.tailOffset, tailText)
                    context.commitDocument()
                    context.editor.caretModel.moveToOffset(context.tailOffset)
                }
        }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private val KaType.text: String
    get() = render(
        renderer = NoAnnotationsTypeRenderer,
        position = Variance.INVARIANT,
    )
