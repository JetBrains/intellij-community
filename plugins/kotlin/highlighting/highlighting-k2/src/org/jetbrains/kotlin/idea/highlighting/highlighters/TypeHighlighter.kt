// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.highlighter.HighlightingFactory
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*

context(KaSession)
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

        val symbol = expression.mainReference.resolveToSymbol() as? KaClassifierSymbol ?: return

        if (isAnnotationCall(expression, symbol)) {
            // higlighted by AnnotationEntryHiglightingVisitor
            return
        }

        val color = when (symbol) {
            is KaAnonymousObjectSymbol -> KotlinHighlightInfoTypeSemanticNames.CLASS
            is KaNamedClassSymbol -> when (symbol.classKind) {
                KaClassKind.CLASS -> if (symbol.isData) {
                    KotlinHighlightInfoTypeSemanticNames.DATA_CLASS
                } else {
                    when (symbol.modality) {
                        KaSymbolModality.FINAL, KaSymbolModality.SEALED, KaSymbolModality.OPEN -> KotlinHighlightInfoTypeSemanticNames.CLASS
                        KaSymbolModality.ABSTRACT -> KotlinHighlightInfoTypeSemanticNames.ABSTRACT_CLASS
                    }
                }
                KaClassKind.ENUM_CLASS -> KotlinHighlightInfoTypeSemanticNames.ENUM
                KaClassKind.ANNOTATION_CLASS -> KotlinHighlightInfoTypeSemanticNames.ANNOTATION
                KaClassKind.OBJECT ->
                    if (symbol.isData) KotlinHighlightInfoTypeSemanticNames.DATA_OBJECT else KotlinHighlightInfoTypeSemanticNames.OBJECT
                KaClassKind.COMPANION_OBJECT -> KotlinHighlightInfoTypeSemanticNames.OBJECT
                KaClassKind.INTERFACE -> KotlinHighlightInfoTypeSemanticNames.TRAIT
                KaClassKind.ANONYMOUS_OBJECT -> KotlinHighlightInfoTypeSemanticNames.CLASS
            }

            is KaTypeAliasSymbol -> KotlinHighlightInfoTypeSemanticNames.TYPE_ALIAS
            is KaTypeParameterSymbol -> KotlinHighlightInfoTypeSemanticNames.TYPE_PARAMETER
        }

        holder.add(HighlightingFactory.highlightName(expression, color)?.create())
    }

    private fun isAnnotationCall(expression: KtSimpleNameExpression, target: KaSymbol): Boolean {
        val isKotlinAnnotation = target is KaConstructorSymbol
                && target.isPrimary
                && (target.containingDeclaration as? KaClassSymbol)?.classKind == KaClassKind.ANNOTATION_CLASS

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