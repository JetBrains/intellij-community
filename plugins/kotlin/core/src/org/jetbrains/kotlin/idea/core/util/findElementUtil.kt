// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isTypeConstructorReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.receivers.ClassQualifier

enum class ElementKind {
    EXPRESSION {
        override val elementClass = KtExpression::class.java
    },
    TYPE_ELEMENT {
        override val elementClass = KtTypeElement::class.java
    },
    TYPE_CONSTRUCTOR {
        override val elementClass = KtSimpleNameExpression::class.java
    };

    abstract val elementClass: Class<out KtElement>
}

fun findElement(
    file: PsiFile,
    startOffset: Int,
    endOffset: Int,
    elementKind: ElementKind
): PsiElement? {
    val element = CodeInsightUtils.findElementOfClassAtRange(
        file, startOffset, endOffset, elementKind.elementClass
    ) ?: return null
    return when(elementKind) {
        ElementKind.TYPE_ELEMENT -> null
        ElementKind.TYPE_CONSTRUCTOR -> element.takeIf(::isTypeConstructorReference)
        ElementKind.EXPRESSION -> findExpression(element)
    }
}

private fun findExpression(element: KtElement): KtExpression? {
    var expression = element
    if (expression is KtScriptInitializer) {
        expression = expression.body ?: return null
    }

    // TODO: Support binary operations in "Introduce..." refactorings
    if (expression is KtOperationReferenceExpression &&
        expression.getReferencedNameElementType() !== KtTokens.IDENTIFIER &&
        expression.getParent() is KtBinaryExpression) {
        return null
    }

    // For cases like 'this@outerClass', don't return the label part
    if (KtPsiUtil.isLabelIdentifierExpression(expression)) {
        expression = PsiTreeUtil.getParentOfType(expression, KtExpression::class.java) ?: return null
    }

    if (expression is KtBlockExpression) {
        val statements = expression.statements
        if (statements.size == 1) {
            val statement = statements[0]
            if (statement.text == expression.text) {
                return statement
            }
        }
    }

    if (expression !is KtExpression) return null

    val qualifier = expression.analyze().get(BindingContext.QUALIFIER, expression)
    if (qualifier != null && (qualifier !is ClassQualifier || qualifier.descriptor.kind != ClassKind.OBJECT)) {
        return null
    }
    return expression
}
