// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter.visitors

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableSymbol
import org.jetbrains.kotlin.idea.KotlinIdeaAnalysisBundle
import org.jetbrains.kotlin.idea.fir.highlighter.textAttributesKeyForPropertyDeclaration
import org.jetbrains.kotlin.idea.highlighter.NameHighlighter
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtInstanceExpressionWithLabel
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

internal class VariableReferenceHighlightingVisitor(
    analysisSession: KtAnalysisSession,
    holder: AnnotationHolder
) : FirAfterResolveHighlightingVisitor(analysisSession, holder) {
    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (!NameHighlighter.namesHighlightingEnabled) return
        if (expression.isAssignmentReference()) return
        if (expression.isByNameArgumentReference()) return
        if (expression.parent is KtInstanceExpressionWithLabel) return

        if (expression.isAutoCreatedItParameter()) {
            createInfoAnnotation(
                expression,
                KotlinIdeaAnalysisBundle.message("automatically.declared.based.on.the.expected.type"),
                Colors.FUNCTION_LITERAL_DEFAULT_PARAMETER
            )
            return
        }

        with(analysisSession) {
            val targetSymbol = expression.mainReference.resolveToSymbol()
            val targetPsi = targetSymbol?.psi
            when {
                targetSymbol is KtBackingFieldSymbol -> Colors.BACKING_FIELD_VARIABLE
                targetSymbol is KtSyntheticJavaPropertySymbol -> Colors.SYNTHETIC_EXTENSION_PROPERTY
                targetPsi != null -> textAttributesKeyForPropertyDeclaration(targetPsi)
                else -> null
            }?.let { attribute ->
                highlightName(expression, attribute)
                if (isMutableVariable(targetSymbol) == true
                    || targetSymbol != null && isBackingFieldReferencingMutableVariable(targetSymbol)
                ) {
                    highlightName(expression, Colors.MUTABLE_VARIABLE)
                }
            }
        }
    }

    @Suppress("unused")
    private fun KtAnalysisSession.isBackingFieldReferencingMutableVariable(symbol: KtSymbol): Boolean {
        if (symbol !is KtBackingFieldSymbol) return false
        return !symbol.owningProperty.isVal
    }

    private fun KtSimpleNameExpression.isByNameArgumentReference() =
        parent is KtValueArgumentName


    private fun KtSimpleNameExpression.isAutoCreatedItParameter(): Boolean {
        return getReferencedName() == "it" // todo
    }
}

@Suppress("unused")
private fun KtAnalysisSession.isMutableVariable(symbol: KtSymbol?): Boolean = when (symbol) {
    is KtVariableSymbol -> !symbol.isVal
    else -> false
}

private fun KtSimpleNameExpression.isAssignmentReference(): Boolean {
    if (this !is KtOperationReferenceExpression) return false
    return operationSignTokenType == KtTokens.EQ
}

