// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfo
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
  project: Project,
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
        if (expression.isCalleeExpression()) return emptyList()
        val parent = expression.parent

        if (parent is KtInstanceExpressionWithLabel) {
            // Do nothing: 'super' and 'this' are highlighted as a keyword
            return emptyList()
        }
        if (expression.isConstructorCallReference()) {
            // Do not highlight constructor call as class reference
            return emptyList()
        }

        val symbol = expression.mainReference.resolveToSymbol() as? KtClassifierSymbol ?: return emptyList()
        if (isAnnotationCall(expression, symbol)) {
            // higlighted by AnnotationEntryHiglightingVisitor
            return emptyList()
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

        return listOfNotNull(highlightName (expression.textRange, color))
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