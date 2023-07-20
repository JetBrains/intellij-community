// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("FindElementUtils")

package org.jetbrains.kotlin.idea.util

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.idea.base.psi.getElementAtOffsetIgnoreWhitespaceAfter
import org.jetbrains.kotlin.idea.base.psi.getElementAtOffsetIgnoreWhitespaceBefore
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.isTypeConstructorReference
import org.jetbrains.kotlin.psi.psiUtil.startOffset

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
    val element = findElementOfClassAtRange(
        file, startOffset, endOffset, elementKind.elementClass
    ) ?: return null

    return when(elementKind) {
        ElementKind.TYPE_ELEMENT -> element
        ElementKind.TYPE_CONSTRUCTOR -> element.takeIf(::isTypeConstructorReference)
        ElementKind.EXPRESSION -> findExpression(element)
    }
}

private fun <T : PsiElement?> findElementOfClassAtRange(file: PsiFile, startOffset: Int, endOffset: Int, aClass: Class<T>): T? {
    // When selected range is this@Fo<select>o</select> we'd like to return `@Foo`
    // But it's PSI looks like: (AT IDENTIFIER):JetLabel
    // So if we search parent starting exactly at IDENTIFIER then we find nothing
    // Solution is to retrieve label if we are on AT or IDENTIFIER
    val element1 = getParentLabelOrElement(getElementAtOffsetIgnoreWhitespaceBefore(file, startOffset)) ?: return null
    val element2 = getParentLabelOrElement(getElementAtOffsetIgnoreWhitespaceAfter(file, endOffset)) ?: return null
    val newStartOffset = element1.startOffset
    val newEndOffset = element2.endOffset
    val newElement = PsiTreeUtil.findElementOfClassAtRange(file, newStartOffset, newEndOffset, aClass) ?: return null
    if (newElement.startOffset != newStartOffset || newElement.endOffset != newEndOffset) {
        return null
    }
    return newElement
}

private fun getParentLabelOrElement(element: PsiElement?): PsiElement? =
    if (element != null && element.parent is KtLabelReferenceExpression)
        element.parent
    else
        element

fun findElements(file: PsiFile, startOffset: Int, endOffset: Int, kind: ElementKind): Array<PsiElement> {
    var element1 = getElementAtOffsetIgnoreWhitespaceBefore(file, startOffset) ?: return PsiElement.EMPTY_ARRAY
    var element2 = getElementAtOffsetIgnoreWhitespaceAfter(file, endOffset) ?: return PsiElement.EMPTY_ARRAY
    val newStartOffset = element1.startOffset
    val newEndOffset = element2.endOffset
    if (newStartOffset >= newEndOffset) return PsiElement.EMPTY_ARRAY

    var parent: PsiElement? = PsiTreeUtil.findCommonParent(element1, element2) ?: return PsiElement.EMPTY_ARRAY
    while (parent !is KtBlockExpression) {
        if (parent == null || parent is KtFile) return PsiElement.EMPTY_ARRAY
        parent = parent.parent
    }

    element1 = getTopmostParentInside(element1, parent)
    if (newStartOffset != element1.startOffset) return PsiElement.EMPTY_ARRAY

    element2 = getTopmostParentInside(element2, parent)
    if (newEndOffset != element2.endOffset) return PsiElement.EMPTY_ARRAY

    val stopElement = element2.nextSibling
    val result = element1.siblings()
        .takeWhile { it !== stopElement }
        .filter { it !is PsiWhiteSpace }
        .toList()
    if (result.all { it.matchesKindOrCanBeSkipped(kind) }) {
        return PsiUtilCore.toPsiElementArray(result)
    }
    return PsiElement.EMPTY_ARRAY
}

@SafeVarargs
fun findElementsOfClassInRange(file: PsiFile, startOffset: Int, endOffset: Int, vararg classes: Class<out PsiElement>): List<PsiElement> {
    var element1 = getElementAtOffsetIgnoreWhitespaceBefore(file, startOffset)
    var element2 = getElementAtOffsetIgnoreWhitespaceAfter(file, endOffset)
    if (element1 == null || element2 == null) return emptyList()

    val newStartOffset = element1.startOffset
    val newEndOffset = element2.endOffset
    val parent = PsiTreeUtil.findCommonParent(element1, element2) ?: return emptyList()

    element1 = getTopmostParentInside(element1, parent)
    if (newStartOffset != element1.startOffset) return emptyList()

    element2 = getTopmostParentInside(element2, parent)
    if (newEndOffset != element2.endOffset) return emptyList()

    val stopElement = element2.nextSibling
    val result = mutableListOf<PsiElement>()
    var currentElement = element1
    while (currentElement !== stopElement && currentElement != null) {
        for (aClass in classes) {
            if (aClass.isInstance(currentElement)) {
                result.add(currentElement)
            }
            result.addAll(PsiTreeUtil.findChildrenOfType(currentElement, aClass))
        }
        currentElement = currentElement.nextSibling
    }

    if (parent != element1 && parent.startOffset == newStartOffset) {
        for (aClass in classes) {
            if (aClass.isInstance(parent)) {
                result.add(parent)
            }
        }
    }
    return result
}

private fun PsiElement.matchesKindOrCanBeSkipped(kind: ElementKind): Boolean =
    matchesKind(kind) || node.elementType === KtTokens.SEMICOLON || this is PsiWhiteSpace || this is PsiComment

private fun PsiElement.matchesKind(kind: ElementKind): Boolean =
    kind === ElementKind.EXPRESSION && this is KtExpression ||
            kind === ElementKind.TYPE_ELEMENT && this is KtTypeElement ||
            kind === ElementKind.TYPE_CONSTRUCTOR && isTypeConstructorReference(this)

private fun getTopmostParentInside(element: PsiElement, parent: PsiElement): PsiElement {
    var node = element
    if (parent != node) {
        while (parent != node.parent) {
            node = node.parent
        }
    }
    return node
}

private fun findExpression(element: KtElement): KtExpression? {
    var expression = element
    if (expression is KtScript) {
        expression = expression.descendantsOfType<KtScriptInitializer>().singleOrNull() ?: return null
    }

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

    return expression as? KtExpression
}
