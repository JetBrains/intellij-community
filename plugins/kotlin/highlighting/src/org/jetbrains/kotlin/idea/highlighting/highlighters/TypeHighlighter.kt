// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.highlighting.isNameHighlightingEnabled
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

internal class TypeHighlighter(
    holder: AnnotationHolder,
    project: Project,
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
        if (expression.isCalleeExpression()) return
        val parent = expression.parent

        if (parent is KtInstanceExpressionWithLabel) {
            // Do nothing: 'super' and 'this' are highlighted as a keyword
            return
        }
        if (expression.isConstructorCallReference()) {
            // Do not highlight constructor call as class reference
            return
        }

        val symbol = expression.mainReference.resolveToSymbol() as? KtClassifierSymbol ?: return
        if (isAnnotationCall(expression, symbol)) {
            // higlighted by AnnotationEntryHiglightingVisitor
            return
        }

        val color = when (symbol) {
            is KtAnonymousObjectSymbol -> Colors.CLASS
            is KtNamedClassOrObjectSymbol -> when (symbol.classKind) {
                KtClassKind.CLASS -> when (symbol.modality) {
                    Modality.FINAL, Modality.SEALED , Modality.OPEN -> Colors.CLASS
                    Modality.ABSTRACT -> Colors.ABSTRACT_CLASS
                }
                KtClassKind.ENUM_CLASS -> Colors.ENUM
                KtClassKind.ANNOTATION_CLASS -> Colors.ANNOTATION
                KtClassKind.OBJECT -> Colors.OBJECT
                KtClassKind.COMPANION_OBJECT -> Colors.OBJECT
                KtClassKind.INTERFACE -> Colors.TRAIT
                KtClassKind.ANONYMOUS_OBJECT -> Colors.CLASS
            }

            is KtTypeAliasSymbol -> Colors.TYPE_ALIAS
            is KtTypeParameterSymbol -> Colors.TYPE_PARAMETER
        }

        highlightName(expression.textRange, color)
    }

    context(KtAnalysisSession)
    private fun isAnnotationCall(expression: KtSimpleNameExpression, target: KtSymbol): Boolean {
        val isKotlinAnnotation = target is KtConstructorSymbol
                && target.isPrimary
                && (target.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind == KtClassKind.ANNOTATION_CLASS

        if (!isKotlinAnnotation) {
            val targetIsAnnotation = when (val targePsi = target.psi) {
                is KtClass -> targePsi.isAnnotation()
                is PsiClass -> targePsi.isAnnotationType
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