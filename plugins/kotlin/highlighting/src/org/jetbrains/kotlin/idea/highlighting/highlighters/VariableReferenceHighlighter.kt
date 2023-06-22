// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.base.highlighting.isNameHighlightingEnabled
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

internal class VariableReferenceHighlighter(
  project: Project
) : AfterResolveHighlighter(project) {

    context(KtAnalysisSession)
    override fun highlight(element: KtElement): List<HighlightInfo.Builder> {
        return when (element) {
            is KtSimpleNameExpression -> highlightSimpleNameExpression(element)
            else -> emptyList()
        }
    }

    context(KtAnalysisSession)
    private fun highlightSimpleNameExpression(expression: KtSimpleNameExpression): List<HighlightInfo.Builder> {
        if (!expression.project.isNameHighlightingEnabled) return emptyList()
        if (expression.isAssignmentReference()) return emptyList()
        if (expression.isByNameArgumentReference()) return emptyList()
        if (expression.parent is KtInstanceExpressionWithLabel) return emptyList()


        return when (val symbol = expression.mainReference.resolveToSymbol()) {
            is KtBackingFieldSymbol -> highlightBackingField(symbol, expression)
            is KtKotlinPropertySymbol -> highlightProperty(symbol, expression)
            is KtLocalVariableSymbol -> {
                val result = mutableListOf<HighlightInfo.Builder>()
                if (!symbol.isVal) {
                    highlightName(expression, Colors.MUTABLE_VARIABLE)?.let { result.add(it) }
                }
                highlightName(expression, Colors.LOCAL_VARIABLE)?.let { result.add(it) }
                result
            }
            is KtSyntheticJavaPropertySymbol -> listOfNotNull(highlightName (expression, Colors.SYNTHETIC_EXTENSION_PROPERTY))
            is KtValueParameterSymbol -> listOfNotNull(highlightValueParameter (symbol, expression))
            is KtEnumEntrySymbol -> listOfNotNull(highlightName (expression, Colors.ENUM_ENTRY))
            else -> emptyList()
        }

    }

    context(KtAnalysisSession)
    private fun highlightValueParameter(symbol: KtValueParameterSymbol, expression: KtSimpleNameExpression): HighlightInfo.Builder? {
        return when {
            symbol.isImplicitLambdaParameter -> {
                createInfoAnnotation(
                    expression,
                    KotlinBaseHighlightingBundle.message("automatically.declared.based.on.the.expected.type"),
                    Colors.FUNCTION_LITERAL_DEFAULT_PARAMETER
                )
            }

            else -> highlightName(expression, Colors.PARAMETER)
        }
    }

    context(KtAnalysisSession)
    private fun highlightProperty(
        symbol: KtKotlinPropertySymbol,
        expression: KtSimpleNameExpression
    ): List<HighlightInfo.Builder> {
        val result = mutableListOf<HighlightInfo.Builder>()
        if (!symbol.isVal) {
            highlightName(expression, Colors.MUTABLE_VARIABLE)?.let { result.add(it) }
        }
        val hasExplicitGetterOrSetter = symbol.getter?.hasBody == true || symbol.setter?.hasBody == true
        val color = when {
            symbol.isExtension -> Colors.EXTENSION_PROPERTY
            symbol.symbolKind == KtSymbolKind.TOP_LEVEL -> when {
                hasExplicitGetterOrSetter -> Colors.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
                else -> Colors.PACKAGE_PROPERTY
            }

            else -> when {
                hasExplicitGetterOrSetter -> Colors.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
                else -> Colors.INSTANCE_PROPERTY
            }
        }
        highlightName(expression, color)?.let { result.add(it) }
        return result
    }

    context(KtAnalysisSession)
    private fun highlightBackingField(symbol: KtBackingFieldSymbol, expression: KtSimpleNameExpression): List<HighlightInfo.Builder> {
        val result = mutableListOf<HighlightInfo.Builder>()
        if (!symbol.owningProperty.isVal) {
            highlightName(expression, Colors.MUTABLE_VARIABLE)?.let { result.add(it) }
        }
        highlightName(expression, Colors.BACKING_FIELD_VARIABLE)?.let { result.add(it) }
        return result
    }

    private fun KtSimpleNameExpression.isByNameArgumentReference() =
        parent is KtValueArgumentName
}


private fun KtSimpleNameExpression.isAssignmentReference(): Boolean {
    if (this !is KtOperationReferenceExpression) return false
    return operationSignTokenType == KtTokens.EQ
}

