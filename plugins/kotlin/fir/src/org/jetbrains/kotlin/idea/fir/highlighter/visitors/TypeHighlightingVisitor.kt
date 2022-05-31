// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter.visitors

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.highlighting.isNameHighlightingEnabled
import org.jetbrains.kotlin.idea.base.highlighting.textAttributesKeyForTypeDeclaration
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

internal class TypeHighlightingVisitor(
    analysisSession: KtAnalysisSession,
    holder: AnnotationHolder
) : FirAfterResolveHighlightingVisitor(analysisSession, holder) {
    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (!expression.project.isNameHighlightingEnabled) return
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
            highlightName(expression.project, expression.textRange, key)
        }
    }

    private fun isAnnotationCall(expression: KtSimpleNameExpression, target: PsiElement): Boolean {
        val isKotlinAnnotation = target is KtPrimaryConstructor && target.containingClassOrObject?.isAnnotation() == true

        if (!isKotlinAnnotation) {
            val targetIsAnnotation = when (target) {
                is KtClass -> target.isAnnotation()
                is PsiClass -> target.isAnnotationType
                else -> false
            }

            if (!targetIsAnnotation) {
                return false
            }
        }

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