// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.analyzers

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.highlighter.HighlightingFactory.highlightName
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
import org.jetbrains.kotlin.psi.*

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
                highlightName(expression, LOCAL_VARIABLE)?.let { result.add(it) }
                result
            }
            is KaSyntheticJavaPropertySymbol -> buildList {
                addIfNotNull(highlightName(expression, SYNTHETIC_EXTENSION_PROPERTY))
                addIfNotNull(symbol.getHighlightingForMutableVar(expression))
            }
            is KaValueParameterSymbol -> listOfNotNull(highlightValueParameter (symbol, expression))
            is KaEnumEntrySymbol -> listOfNotNull(highlightName (expression, KotlinHighlightInfoTypeSemanticNames.ENUM_ENTRY))
            is KaJavaFieldSymbol -> buildList {
                if (symbol.isStatic) {
                    addIfNotNull(highlightName(expression, PACKAGE_PROPERTY))
                } else {
                    addIfNotNull(highlightName(expression, INSTANCE_PROPERTY))
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
            highlightName(expression, MUTABLE_VARIABLE)
        }
    }

    private fun highlightValueParameter(symbol: KaValueParameterSymbol, expression: KtSimpleNameExpression): HighlightInfo.Builder? {
        return when {
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
    ): List<HighlightInfo.Builder> {
        val result = mutableListOf<HighlightInfo.Builder>()
        if (!symbol.isVal) {
            highlightName(expression, MUTABLE_VARIABLE)?.let { result.add(it) }
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
        (expression.parent as? KtDotQualifiedExpression)?.let { dotQualifiedExpression ->
            if (dotQualifiedExpression.selectorExpression == expression) {
                with(session) {
                    val call = expression.resolveToCall()?.singleCallOrNull<KaCall>() ?: return@with
                    val highlightInfoType = getHighlightInfoTypeForCallFromExtension(expression, call) ?: return@with
                    highlightName(expression, highlightInfoType)?.let { result.add(it) }
                }
            }
        }
        highlightName(expression, color)?.let { result.add(it) }
        return result
    }

    private fun highlightBackingField(symbol: KaBackingFieldSymbol, expression: KtSimpleNameExpression): List<HighlightInfo.Builder> {
        val result = mutableListOf<HighlightInfo.Builder>()
        if (!symbol.owningProperty.isVal) {
            highlightName(expression, MUTABLE_VARIABLE)?.let { result.add(it) }
        }
        highlightName(expression, BACKING_FIELD_VARIABLE)?.let { result.add(it) }
        return result
    }

    private fun KtSimpleNameExpression.isByNameArgumentReference() =
        parent is KtValueArgumentName
}


private fun KtSimpleNameExpression.isAssignmentReference(): Boolean {
    if (this !is KtOperationReferenceExpression) return false
    return operationSignTokenType == KtTokens.EQ
}

