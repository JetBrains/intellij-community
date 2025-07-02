// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.TextResult
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

/**
 * An insertion handler that uses templates for inserting a trailing lambda, adding a
 * live template for each parameter that allows the user to choose between different suggested names.
 *
 * Note: This insertion handler can only be used if the project supports using templates (i.e. not on LSP yet).
 */
@Serializable
internal class TrailingLambdaInsertionHandler private constructor(
    private val parameterData: List<TrailingLambdaParameterData>
) : SerializableInsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement
    ) {
        val template = TemplateManager.getInstance(context.project).createTemplate("", "").apply {
            isToShortenLongNames = true
            isToReformat = false //TODO

            addTextSegment(" { ")

            parameterData.forEachIndexed { index, parameter ->
                val showDefault = parameterData.size != 1
                val lookupElements = parameter.suggestedNames.map { name ->
                    if (showDefault) {
                        LookupElementBuilder.create(name)
                    } else {
                        val tailText = " -> "
                        LookupElementBuilder.create(name + tailText)
                            .withPresentableText(name)
                            .withTailText(tailText, true)
                    }
                }.map { it.withTypeText(parameter.typeText) }

                addVariable(
                    /* expression = */
                    LambdaParameterExpression(
                        lookupElements = lookupElements.toList(),
                        showDefault = showDefault,
                    ),
                    /* isAlwaysStopAt = */ true,
                )

                if (index != parameterData.lastIndex) {
                    addTextSegment(", ")
                } else if (index != 0) {
                    addTextSegment(" -> ")
                }
            }

            addEndVariable()
            addTextSegment(" }")
        }
        TemplateManager.getInstance(context.project).startTemplate(context.editor, template)
    }


    companion object {
        /**
         * Returns the [TrailingLambdaInsertionHandler] if the project supports using templates, null otherwise.
         */
        @OptIn(KaExperimentalApi::class)
        context(KaSession)
        fun create(functionType: KaFunctionType): TrailingLambdaInsertionHandler? {
            if (TemplateManager.getInstance(useSiteModule.project) == null) return null
            val parameterNames = functionType.parameters.map { it.name }

            val data = buildTrailingLambdaParameterData(functionType, parameterNames)
            return TrailingLambdaInsertionHandler(data)
        }
    }
}

@Serializable
private data class TrailingLambdaParameterData(
    val suggestedNames: List<String>,
    val typeText: String,
)

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun buildTrailingLambdaParameterData(
    trailingFunctionType: KaFunctionType,
    suggestedParameterNames: List<Name?> = emptyList(),
): List<TrailingLambdaParameterData> {
    val typeNames = mutableMapOf<KaType, MutableSet<String>>()
    val kotlinNameSuggester = KotlinNameSuggester()

    val namedParameterTypes = trailingFunctionType.parameters
        .zip(suggestedParameterNames)
    return namedParameterTypes.map {(parameter, suggestedName) ->
        val parameterType = parameter.type
        //TODO: check for names in scope
        val validator = typeNames.getOrPut(parameterType) {
            mutableSetOf()
        }::add

        val initialSuggestedNames = suggestedName?.let { sequenceOf(it.asString()) }
            ?: kotlinNameSuggester.suggestTypeNames(parameterType)

        val parameterTypeText = parameterType.render(
            renderer = NoAnnotationsTypeRenderer,
            position = Variance.INVARIANT,
        )

        val suggestedNames = initialSuggestedNames.map {
            KotlinNameSuggester.suggestNameByName(it, validator)
        }.toList()
        TrailingLambdaParameterData(suggestedNames, parameterTypeText)
    }
}

private class LambdaParameterExpression(
    private val lookupElements: Collection<LookupElement>,
    private val showDefault: Boolean = true,
) : Expression() {

    override fun calculateResult(context: ExpressionContext): TextResult? =
        lookupElements.takeIf { showDefault }
            ?.firstOrNull()
            ?.lookupString
            ?.let { TextResult(it) }

    override fun calculateLookupItems(context: ExpressionContext): Array<LookupElement> =
        lookupElements.toTypedArray()
}

@KaExperimentalApi
private val NoAnnotationsTypeRenderer: KaTypeRenderer = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
    annotationsRenderer = annotationsRenderer.with {
        annotationFilter = KaRendererAnnotationsFilter.NONE
    }
}