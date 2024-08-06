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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render

@KaExperimentalApi
private val NoAnnotationsTypeRenderer: KaTypeRenderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
    annotationsRenderer = annotationsRenderer.with {
        annotationFilter = KaRendererAnnotationsFilter.NONE
    }
}

context(KaSession)
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
    val parameterTypes = trailingFunctionType.parameterTypes
        .zip(suggestedParameterNames)
    val iterator = parameterTypes.iterator()
    while (iterator.hasNext()) {
        val (parameterType, suggestedName) = iterator.next()

        //TODO: check for names in scope
        val validator = typeNames.getOrPut(parameterType) {
            mutableSetOf()
        }::add

        val items = (suggestedName?.let { sequenceOf(it.render()) }
            ?: kotlinNameSuggester.suggestTypeNames(parameterType))
            .map { KotlinNameSuggester.suggestNameByName(it, validator) }
            .toList()

        addVariable(
            /* expression = */ LambdaParameterExpression(items),
            /* isAlwaysStopAt = */ true,
        )

        //parameterType.render(
        //    renderer = NoAnnotationsTypeRenderer,
        //    position = Variance.INVARIANT,
        //).let { typeText ->
        //    addTextSegment(KtTokens.COLON.value)
        //    addTextSegment(" ")
        //    addTextSegment(typeText)
        //}

        if (iterator.hasNext()) {
            addTextSegment(KtTokens.COMMA.value)
            addTextSegment(" ")
        }
    }

    if (parameterTypes.isNotEmpty()) {
        addTextSegment(" ")
        addTextSegment(KtTokens.ARROW.value)
        addTextSegment(" ")
    }
    addEndVariable()
    addTextSegment(" }")
}

private class LambdaParameterExpression(
    private val items: List<String>,
) : Expression() {

    override fun calculateResult(context: ExpressionContext): TextResult =
        TextResult(items.first())

    override fun calculateLookupItems(context: ExpressionContext): Array<LookupElement> =
        items.map { LookupElementBuilder.create(it) }
            .toTypedArray()
}
