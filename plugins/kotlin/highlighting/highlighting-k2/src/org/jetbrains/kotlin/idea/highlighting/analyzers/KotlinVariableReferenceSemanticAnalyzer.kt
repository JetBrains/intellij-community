// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.analyzers

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.BACKING_FIELD_VARIABLE
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.EXTENSION_PROPERTY
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.FUNCTION_LITERAL_DEFAULT_PARAMETER
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.LOCAL_VARIABLE
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.MUTABLE_VARIABLE
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.PARAMETER
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames.SYNTHETIC_EXTENSION_PROPERTY
import org.jetbrains.kotlin.idea.highlighting.analyzers.KotlinFunctionCallSemanticAnalyzer.Companion.getHighlightInfoTypeForCallFromExtension
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtInstanceExpressionWithLabel
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgumentName

internal class KotlinVariableReferenceSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession) : KotlinSemanticAnalyzer(holder, session) {
    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        highlightSimpleNameExpression(expression)
    }

    private fun highlightSimpleNameExpression(expression: KtSimpleNameExpression) {
        context(session) {
            if (expression.isAssignmentReference()) return
            if (expression.isByNameArgumentReference()) return
            if (expression.parent is KtInstanceExpressionWithLabel) return

            when (val symbol = expression.mainReference.resolveToSymbol()) {
                is KaBackingFieldSymbol -> highlightBackingField(symbol, expression)
                is KaKotlinPropertySymbol -> highlightProperty(symbol, expression)
                is KaLocalVariableSymbol -> {
                    symbol.getHighlightingForMutableVar(expression)
                    highlightName(expression, LOCAL_VARIABLE)
                }

                is KaSyntheticJavaPropertySymbol -> {
                    highlightName(expression, SYNTHETIC_EXTENSION_PROPERTY)
                    symbol.getHighlightingForMutableVar(expression)
                }

                is KaValueParameterSymbol -> listOfNotNull(highlightValueParameter(symbol, expression))
                is KaEnumEntrySymbol -> listOfNotNull(highlightName(expression, KotlinHighlightInfoTypeSemanticNames.ENUM_ENTRY))
                is KaJavaFieldSymbol -> buildList {
                    if (symbol.isStatic) {
                        addIfNotNull(highlightName(expression, PACKAGE_PROPERTY))
                    } else {
                        addIfNotNull(highlightName(expression, INSTANCE_PROPERTY))
                    }
                    addIfNotNull(symbol.getHighlightingForMutableVar(expression))
                }

                else -> return
            }
        }
    }

    private fun KaVariableSymbol.getHighlightingForMutableVar(expression: KtSimpleNameExpression) {
        if (isVal) return

        highlightName(expression, MUTABLE_VARIABLE)
    }

    private fun highlightValueParameter(symbol: KaValueParameterSymbol, expression: KtSimpleNameExpression) {
        when {
            symbol.isImplicitLambdaParameter -> {
                highlightName(
                    expression,
                    FUNCTION_LITERAL_DEFAULT_PARAMETER,
                    KotlinBaseHighlightingBundle.message("automatically.declared.based.on.the.expected.type")
                )
            }

            else -> highlightName(expression, PARAMETER)
        }
    }

    private fun highlightProperty(
        symbol: KaKotlinPropertySymbol,
        expression: KtSimpleNameExpression
    ) {
        val extHighlightInfoType = getHighlightingInfoTypeForPropertyCallFromExtension(expression)
        if (extHighlightInfoType) return

        getDefaultHighlightingInfoForPropertyCall(symbol, expression)
    }

    private fun getHighlightingInfoTypeForPropertyCallFromExtension(expression: KtSimpleNameExpression): Boolean {
        val highlightInfoType: HighlightInfoType = context(session) {
            val call = expression.resolveToCall()?.singleCallOrNull<KaCall>() ?: return false
            getHighlightInfoTypeForCallFromExtension(expression, call)
        } ?: return false

        highlightName(expression, highlightInfoType)
        return true
    }

    private fun getDefaultHighlightingInfoForPropertyCall(
        symbol: KaKotlinPropertySymbol,
        expression: KtSimpleNameExpression
    ) {
        if (!symbol.isVal) {
            highlightName(expression, MUTABLE_VARIABLE)
        }

        val hasExplicitGetterOrSetter = symbol.getter?.hasBody == true || symbol.setter?.hasBody == true
        val color = when {
            symbol.isExtension -> EXTENSION_PROPERTY
            symbol.location == KaSymbolLocation.TOP_LEVEL -> when {
                hasExplicitGetterOrSetter -> PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
                else -> PACKAGE_PROPERTY
            }

            else -> when {
                hasExplicitGetterOrSetter -> INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
                else -> INSTANCE_PROPERTY
            }
        }
        highlightName(expression, color)
    }

    private fun highlightBackingField(symbol: KaBackingFieldSymbol, expression: KtSimpleNameExpression) {
        if (!symbol.owningProperty.isVal) {
            highlightName(expression, MUTABLE_VARIABLE)
        }
        highlightName(expression, BACKING_FIELD_VARIABLE)
    }

    private fun KtSimpleNameExpression.isByNameArgumentReference() =
        parent is KtValueArgumentName
}


private fun KtSimpleNameExpression.isAssignmentReference(): Boolean {
    if (this !is KtOperationReferenceExpression) return false
    return operationSignTokenType == KtTokens.EQ
}

