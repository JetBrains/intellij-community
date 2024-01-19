// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.highlighting.HighlightingFactory
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*

context(KtAnalysisSession)
internal class TypeHighlighter(holder: HighlightInfoHolder) : KotlinSemanticAnalyzer(holder) {
    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        highlightSimpleNameExpression(expression)
    }

    private fun highlightSimpleNameExpression(expression: KtSimpleNameExpression) {
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

        holder.add(HighlightingFactory.highlightName(expression, color)?.create())
    }

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

fun KtSimpleNameExpression.isCalleeExpression(): Boolean =
    (parent as? KtCallExpression)?.calleeExpression == this

fun KtSimpleNameExpression.isConstructorCallReference(): Boolean {
    val type = parent as? KtUserType ?: return false
    val typeReference = type.parent as? KtTypeReference ?: return false
    val constructorCallee = typeReference.parent as? KtConstructorCalleeExpression ?: return false
    return constructorCallee.constructorReferenceExpression == this
}