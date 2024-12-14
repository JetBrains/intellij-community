// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.analyzers

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.highlighter.HighlightingFactory
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtInstanceExpressionWithLabel
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgumentName

internal class KotlinVariableReferenceSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession) : KotlinSemanticAnalyzer(holder, session) {
    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        highlightSimpleNameExpression(expression).forEach { holder.add(it.create()) }
    }

    private fun highlightSimpleNameExpression(expression: KtSimpleNameExpression): List<HighlightInfo.Builder> = with(session) {
        if (expression.isAssignmentReference()) return emptyList()
        if (expression.isByNameArgumentReference()) return emptyList()
        if (expression.parent is KtInstanceExpressionWithLabel) return emptyList()

        return when (val symbol = expression.mainReference.resolveToSymbol()) {
            is KaBackingFieldSymbol -> highlightBackingField(symbol, expression)
            is KaKotlinPropertySymbol -> highlightProperty(symbol, expression)
            is KaLocalVariableSymbol -> {
                val result = mutableListOf<HighlightInfo.Builder>()
                result.addIfNotNull(symbol.getHighlightingForMutableVar(expression))
                HighlightingFactory.highlightName(expression, KotlinHighlightInfoTypeSemanticNames.LOCAL_VARIABLE)?.let { result.add(it) }
                result
            }
            is KaSyntheticJavaPropertySymbol -> buildList {
                addIfNotNull(HighlightingFactory.highlightName(expression, KotlinHighlightInfoTypeSemanticNames.SYNTHETIC_EXTENSION_PROPERTY))
                addIfNotNull(symbol.getHighlightingForMutableVar(expression))
            }
            is KaValueParameterSymbol -> listOfNotNull(highlightValueParameter (symbol, expression))
            is KaEnumEntrySymbol -> listOfNotNull(HighlightingFactory.highlightName (expression, KotlinHighlightInfoTypeSemanticNames.ENUM_ENTRY))
            is KaJavaFieldSymbol -> buildList {
                if (symbol.isStatic) {
                    addIfNotNull(HighlightingFactory.highlightName(expression, KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY))
                } else {
                    addIfNotNull(HighlightingFactory.highlightName(expression, KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY))
                }
                addIfNotNull(symbol.getHighlightingForMutableVar(expression))
            }
            else -> emptyList()
        }
    }

    private fun KaVariableSymbol.getHighlightingForMutableVar(expression: KtSimpleNameExpression): HighlightInfo.Builder? {
        return if (isVal) {
            null
        } else {
            HighlightingFactory.highlightName(expression, KotlinHighlightInfoTypeSemanticNames.MUTABLE_VARIABLE)
        }
    }

    private fun highlightValueParameter(symbol: KaValueParameterSymbol, expression: KtSimpleNameExpression): HighlightInfo.Builder? {
        return when {
            symbol.isImplicitLambdaParameter -> {
                HighlightingFactory.highlightName(
                  expression,
                  KotlinHighlightInfoTypeSemanticNames.FUNCTION_LITERAL_DEFAULT_PARAMETER,
                  KotlinBaseHighlightingBundle.message("automatically.declared.based.on.the.expected.type")
                )
            }

            else -> HighlightingFactory.highlightName(expression, KotlinHighlightInfoTypeSemanticNames.PARAMETER)
        }
    }

    private fun highlightProperty(
        symbol: KaKotlinPropertySymbol,
        expression: KtSimpleNameExpression
    ): List<HighlightInfo.Builder> {
        val result = mutableListOf<HighlightInfo.Builder>()
        if (!symbol.isVal) {
            HighlightingFactory.highlightName(expression, KotlinHighlightInfoTypeSemanticNames.MUTABLE_VARIABLE)?.let { result.add(it) }
        }
        val hasExplicitGetterOrSetter = symbol.getter?.hasBody == true || symbol.setter?.hasBody == true
        val color = when {
            symbol.isExtension -> KotlinHighlightInfoTypeSemanticNames.EXTENSION_PROPERTY
            symbol.location == KaSymbolLocation.TOP_LEVEL -> when {
                hasExplicitGetterOrSetter -> KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
                else -> KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY
            }

            else -> when {
                hasExplicitGetterOrSetter -> KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
                else -> KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY
            }
        }
        HighlightingFactory.highlightName(expression, color)?.let { result.add(it) }
        return result
    }

    private fun highlightBackingField(symbol: KaBackingFieldSymbol, expression: KtSimpleNameExpression): List<HighlightInfo.Builder> {
        val result = mutableListOf<HighlightInfo.Builder>()
        if (!symbol.owningProperty.isVal) {
            HighlightingFactory.highlightName(expression, KotlinHighlightInfoTypeSemanticNames.MUTABLE_VARIABLE)?.let { result.add(it) }
        }
        HighlightingFactory.highlightName(expression, KotlinHighlightInfoTypeSemanticNames.BACKING_FIELD_VARIABLE)?.let { result.add(it) }
        return result
    }

    private fun KtSimpleNameExpression.isByNameArgumentReference() =
        parent is KtValueArgumentName
}


private fun KtSimpleNameExpression.isAssignmentReference(): Boolean {
    if (this !is KtOperationReferenceExpression) return false
    return operationSignTokenType == KtTokens.EQ
}

