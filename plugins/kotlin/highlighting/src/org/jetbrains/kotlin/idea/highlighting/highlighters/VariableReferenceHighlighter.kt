// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.base.highlighting.isNameHighlightingEnabled
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

internal class VariableReferenceHighlighter(
    holder: AnnotationHolder,
    project: Project
) : AfterResolveHighlighter(holder, project) {

    context(KtAnalysisSession)
    override fun highlight(element: KtElement) {
        when (element) {
            is KtSimpleNameExpression -> highlightSimpleNameExpression(element)
            else -> {}
        }
    }

    context(KtAnalysisSession)
    private fun highlightSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (!expression.project.isNameHighlightingEnabled) return
        if (expression.isAssignmentReference()) return
        if (expression.isByNameArgumentReference()) return
        if (expression.parent is KtInstanceExpressionWithLabel) return

        when (val symbol = expression.mainReference.resolveToSymbol()) {
            is KtBackingFieldSymbol -> highlightBackingField(symbol, expression)
            is KtKotlinPropertySymbol -> highlightProperty(symbol, expression)
            is KtLocalVariableSymbol -> {
                if (!symbol.isVal) {
                    highlightName(expression, Colors.MUTABLE_VARIABLE)
                }
                highlightName(expression, Colors.LOCAL_VARIABLE)
            }
            is KtSyntheticJavaPropertySymbol -> highlightName(expression, Colors.SYNTHETIC_EXTENSION_PROPERTY)
            is KtValueParameterSymbol -> highlightValueParameter(symbol, expression)
            is KtEnumEntrySymbol -> highlightName(expression, Colors.ENUM_ENTRY)
        }

    }

    context(KtAnalysisSession)
    private fun highlightValueParameter(symbol: KtValueParameterSymbol, expression: KtSimpleNameExpression) {
        when {
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
    ) {
        if (!symbol.isVal) {
            highlightName(expression, Colors.MUTABLE_VARIABLE)
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
        highlightName(expression, color)
    }

    context(KtAnalysisSession)
    private fun highlightBackingField(symbol: KtBackingFieldSymbol, expression: KtSimpleNameExpression) {
        if (!symbol.owningProperty.isVal) {
            highlightName(expression, Colors.MUTABLE_VARIABLE)
        }
        highlightName(expression, Colors.BACKING_FIELD_VARIABLE)
    }

    private fun KtSimpleNameExpression.isByNameArgumentReference() =
        parent is KtValueArgumentName
}


private fun KtSimpleNameExpression.isAssignmentReference(): Boolean {
    if (this !is KtOperationReferenceExpression) return false
    return operationSignTokenType == KtTokens.EQ
}

