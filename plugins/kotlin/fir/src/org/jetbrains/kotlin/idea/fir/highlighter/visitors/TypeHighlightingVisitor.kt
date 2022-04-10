// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter.visitors

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.highlighter.isAnnotationClass
import org.jetbrains.kotlin.idea.highlighter.textAttributesKeyForTypeDeclaration
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.highlighter.NameHighlighter
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

internal class TypeHighlightingVisitor(
    analysisSession: KtAnalysisSession,
    holder: AnnotationHolder
) : FirAfterResolveHighlightingVisitor(analysisSession, holder) {
    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (!NameHighlighter.namesHighlightingEnabled) return
        if (expression.isCalleeExpression()) return
        val parent = expression.parent

        if (parent is KtInstanceExpressionWithLabel) {
            // Do nothing: 'super' and 'this' are highlighted as a keyword
            return
        }
        val target = expression.mainReference.resolve() ?: return
        if (isAnnotationCall(expression, target)) {
            // higlighted by AnnotationEntryHiglightingVisitor
            return
        }
        textAttributesKeyForTypeDeclaration(target)?.let { key ->
            if (expression.isConstructorCallReference() && key != Colors.ANNOTATION) {
                // Do not highlight constructor call as class reference
                return@let
            }
            highlightName(expression.textRange, key)
        }
    }

    private fun isAnnotationCall(expression: KtSimpleNameExpression, target: PsiElement): Boolean {
        val expressionRange = expression.textRange

        val isKotlinAnnotation = target is KtPrimaryConstructor && target.parent.isAnnotationClass()
        if (!isKotlinAnnotation && !target.isAnnotationClass()) return false

        val annotationEntry = PsiTreeUtil.getParentOfType(
            expression, KtAnnotationEntry::class.java, /* strict = */false, KtValueArgumentList::class.java
        )
       return annotationEntry?.atSymbol != null
    }
}

private fun KtSimpleNameExpression.isCalleeExpression() =
    (parent as? KtCallExpression)?.calleeExpression == this

private fun KtSimpleNameExpression.isConstructorCallReference(): Boolean {
    val type = parent as? KtUserType ?: return false
    val typeReference = type.parent as? KtTypeReference ?: return false
    val constructorCallee = typeReference.parent as? KtConstructorCalleeExpression ?: return false
    return constructorCallee.constructorReferenceExpression == this
}