// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinPsiUtils")

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentsOfType
import com.intellij.util.castSafelyTo
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.util.takeWhileNotNull

val KtClassOrObject.classIdIfNonLocal: ClassId?
    get() {
        if (KtPsiUtil.isLocal(this)) return null
        val packageName = containingKtFile.packageFqName
        val classesNames = parentsOfType<KtDeclaration>().map { it.name }.toList().asReversed()
        if (classesNames.any { it == null }) return null
        return ClassId(packageName, FqName(classesNames.joinToString(separator = ".")), /*local=*/false)
    }

fun getElementAtOffsetIgnoreWhitespaceBefore(file: PsiFile, offset: Int): PsiElement? {
    val element = file.findElementAt(offset)
    if (element is PsiWhiteSpace) {
        return file.findElementAt(element.getTextRange().endOffset)
    }
    return element
}

fun getElementAtOffsetIgnoreWhitespaceAfter(file: PsiFile, offset: Int): PsiElement? {
    val element = file.findElementAt(offset - 1)
    if (element is PsiWhiteSpace) {
        return file.findElementAt(element.getTextRange().startOffset - 1)
    }
    return element
}

fun getStartLineOffset(file: PsiFile, line: Int): Int? {
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
    if (line >= document.lineCount) {
        return null
    }

    val lineStartOffset = document.getLineStartOffset(line)
    return CharArrayUtil.shiftForward(document.charsSequence, lineStartOffset, " \t")
}

fun getEndLineOffset(file: PsiFile, line: Int): Int? {
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
    if (line >= document.lineCount) {
        return null
    }

    val lineStartOffset = document.getLineEndOffset(line)
    return CharArrayUtil.shiftBackward(document.charsSequence, lineStartOffset, " \t")
}

fun getTopmostElementAtOffset(element: PsiElement, offset: Int): PsiElement {
    var node = element
    do {
        val parent = node.parent
        if (parent == null || !parent.isSuitableTopmostElementAtOffset(offset)) {
            break
        }
        node = parent
    } while (true)

    return node
}

fun getTopParentWithEndOffset(element: PsiElement, stopAt: Class<*>): PsiElement {
    var node = element
    val endOffset = node.textOffset + node.textLength
    do {
        val parent = node.parent ?: break
        if (parent.textOffset + parent.textLength != endOffset) {
            break
        }

        node = parent
        if (stopAt.isInstance(node)) {
            break
        }
    } while (true)

    return node
}

@SafeVarargs
@Suppress("UNCHECKED_CAST")
fun <T> getTopmostElementAtOffset(element: PsiElement, offset: Int, vararg classes: Class<out T>): T? {
    var node = element
    var lastElementOfType: T? = null
    if (classes.anyIsInstance(node)) {
        lastElementOfType = node as? T
    }

    do {
        val parent = node.parent
        if (parent == null || !parent.isSuitableTopmostElementAtOffset(offset)) {
            break
        }
        if (classes.anyIsInstance(parent)) {
            lastElementOfType = parent as? T
        }
        node = parent
    } while (true)

    return lastElementOfType
}

private fun <T> Array<out Class<out T>>.anyIsInstance(element: PsiElement): Boolean =
    any { it.isInstance(element) }

private fun PsiElement.isSuitableTopmostElementAtOffset(offset: Int): Boolean =
    textOffset >= offset && this !is KtBlockExpression && this !is PsiFile


fun KtExpression.safeDeparenthesize(): KtExpression = KtPsiUtil.safeDeparenthesize(this)

fun KtDeclaration.isExpectDeclaration(): Boolean =
    when {
        hasExpectModifier() -> true
        else -> containingClassOrObject?.isExpectDeclaration() == true
    }

fun KtPropertyAccessor.deleteBody() {
    val leftParenthesis = leftParenthesis ?: return
    deleteChildRange(leftParenthesis, lastChild)
}

fun KtDeclarationWithBody.singleExpressionBody(): KtExpression? =
    when (val body = bodyExpression) {
        is KtBlockExpression -> body.statements.singleOrNull()?.castSafelyTo<KtReturnExpression>()?.returnedExpression
        else -> body
    }

fun KtExpression.getCallChain(): List<KtExpression> =
    generateSequence<Pair<KtExpression?, KtExpression?>>(this to null) { (receiver, _) ->
        receiver.castSafelyTo<KtDotQualifiedExpression>()?.let { it.receiverExpression to it.selectorExpression } ?: (null to receiver)
    }
        .drop(1)
        .map { (_, selector) -> selector }
        .takeWhileNotNull()
        .toList()
        .reversed()

