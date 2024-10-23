// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates

import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TextResult
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
@OptIn(KaExperimentalApi::class)
fun Template.build(
    parametersCount: Int,
    trailingFunctionType: KaFunctionType,
    suggestedParameterNames: List<Name?> = emptyList(),
): Template = apply {
    isToShortenLongNames = true
    // isToReformat = true //TODO

    for (index in 0 until parametersCount) {
        if (index == 0) {
            addTextSegment("(")
        }

        addVariable(
            /* expression = */ EmptyExpression(),
            /* isAlwaysStopAt = */ true,
        )

        addTextSegment(if (index == parametersCount - 1) ")" else ", ")
    }

    val typeNames = mutableMapOf<KaType, MutableSet<String>>()
    val kotlinNameSuggester = KotlinNameSuggester()

    addTextSegment(" { ")

    val namedParameterTypes = trailingFunctionType.parameterTypes
        .zip(suggestedParameterNames)
    namedParameterTypes.forEachIndexed { index, (parameterType, suggestedName) ->
        //TODO: check for names in scope
        val validator = typeNames.getOrPut(parameterType) {
            mutableSetOf()
        }::add

        val suggestedNames = suggestedName?.let { sequenceOf(it.asString()) }
            ?: kotlinNameSuggester.suggestTypeNames(parameterType)

        val parameterTypeText = parameterType.render(
            renderer = NoAnnotationsTypeRenderer,
            position = Variance.INVARIANT,
        )

        val showDefault = namedParameterTypes.size != 1
        val lookupElements = suggestedNames.map {
            KotlinNameSuggester.suggestNameByName(it, validator)
        }.map { name ->
            if (showDefault) {
                LookupElementBuilder.create(name)
            } else {
                val tailText = " -> "
                LookupElementBuilder.create(name + tailText)
                    .withPresentableText(name)
                    .withTailText(tailText, true)
            }
        }.map { it.withTypeText(parameterTypeText) }

        addVariable(
            /* expression = */
            LambdaParameterExpression(
                lookupElements = lookupElements.toList(),
                showDefault = showDefault,
            ),
            /* isAlwaysStopAt = */ true,
        )

        if (index != namedParameterTypes.lastIndex) {
            addTextSegment(", ")
        } else if (index != 0) {
            addTextSegment(" -> ")
        }
    }

    addEndVariable()
    addTextSegment(" }")
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
