// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.highlighting.HighlightingFactory
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.highlighting.KotlinRefsHolder
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*

internal class TypeHighlighter(
  project: Project,
  private val kotlinRefsHolder: KotlinRefsHolder
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
        if (expression.isCalleeExpression()) return emptyList()
        val parent = expression.parent

        if (parent is KtInstanceExpressionWithLabel) {
            // Do nothing: 'super' and 'this' are highlighted as a keyword
            return emptyList()
        }
        if (expression.isConstructorCallReference()) {
            kotlinRefsHolder.registerLocalRef((expression.mainReference.resolveToSymbol() as? KtConstructorSymbol)?.psi, expression)
            // Do not highlight constructor call as class reference
            return emptyList()
        }

        val symbol = expression.mainReference.resolveToSymbol() as? KtClassifierSymbol ?: return emptyList()

        kotlinRefsHolder.registerLocalRef(symbol.psi, expression)

        if (isAnnotationCall(expression, symbol)) {
            // higlighted by AnnotationEntryHiglightingVisitor
            return emptyList()
        }

        val color = when (symbol) {
            is KtAnonymousObjectSymbol -> KotlinHighlightInfoTypeSemanticNames.CLASS
            is KtNamedClassOrObjectSymbol -> when (symbol.classKind) {
                KtClassKind.CLASS -> when (symbol.modality) {
                    Modality.FINAL, Modality.SEALED , Modality.OPEN -> KotlinHighlightInfoTypeSemanticNames.CLASS
                    Modality.ABSTRACT -> KotlinHighlightInfoTypeSemanticNames.ABSTRACT_CLASS
                }
                KtClassKind.ENUM_CLASS -> KotlinHighlightInfoTypeSemanticNames.ENUM
                KtClassKind.ANNOTATION_CLASS -> KotlinHighlightInfoTypeSemanticNames.ANNOTATION
                KtClassKind.OBJECT -> KotlinHighlightInfoTypeSemanticNames.OBJECT
                KtClassKind.COMPANION_OBJECT -> KotlinHighlightInfoTypeSemanticNames.OBJECT
                KtClassKind.INTERFACE -> KotlinHighlightInfoTypeSemanticNames.TRAIT
                KtClassKind.ANONYMOUS_OBJECT -> KotlinHighlightInfoTypeSemanticNames.CLASS
            }

            is KtTypeAliasSymbol -> KotlinHighlightInfoTypeSemanticNames.TYPE_ALIAS
            is KtTypeParameterSymbol -> KotlinHighlightInfoTypeSemanticNames.TYPE_PARAMETER
        }

        return listOfNotNull(HighlightingFactory.highlightName(expression, color))
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