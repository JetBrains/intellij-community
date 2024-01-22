// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinPsiUtils")

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.util.asSafely
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

val KtClassOrObject.classIdIfNonLocal: ClassId?
    get() {
        if (KtPsiUtil.isLocal(this)) return null
        val packageName = containingKtFile.packageFqName
        val classesNames = parentsOfType<KtDeclaration>().map { it.name }.toList().asReversed()
        if (classesNames.any { it == null }) return null
        return ClassId(packageName, FqName(classesNames.joinToString(separator = ".")), /*local=*/false)
    }

val KtCallableDeclaration.callableIdIfNotLocal: CallableId?
    get() {
        val callableName = this.nameAsName ?: return null
        if (isTopLevelInFileOrScript(this)) {
            return CallableId(containingKtFile.packageFqName, callableName)
        }

        val classId = containingClassOrObject?.classIdIfNonLocal ?: return null
        return CallableId(classId, callableName)
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
        this is KtParameter -> ownerFunction?.isExpectDeclaration() == true
        else -> containingClassOrObject?.isExpectDeclaration() == true
    }

fun KtDeclaration.isEffectivelyActual(checkConstructor: Boolean = true): Boolean = when {
    hasActualModifier() -> true
    this is KtEnumEntry || checkConstructor && this is KtConstructor<*> -> containingClass()?.hasActualModifier() == true
    else -> false
}

fun KtPropertyAccessor.deleteBody() {
    val leftParenthesis = leftParenthesis ?: return
    deleteChildRange(leftParenthesis, lastChild)
}

/**
 * Does one of two conversions:
 * * `fun foo() = value` -> `value`
 * * `fun foo() { return value }` -> `value`
 */
fun KtDeclarationWithBody.singleExpressionBody(): KtExpression? =
    when (val body = bodyExpression) {
        is KtBlockExpression -> body.statements.singleOrNull()?.asSafely<KtReturnExpression>()?.returnedExpression
        else -> body
    }

fun KtNamedDeclaration.isConstructorDeclaredProperty(): Boolean =
    this is KtParameter && ownerFunction is KtPrimaryConstructor && hasValOrVar()

fun KtExpression.getCallChain(): List<KtExpression> =
    generateSequence(this) { (it as? KtDotQualifiedExpression)?.receiverExpression }
        .map { (it as? KtDotQualifiedExpression)?.selectorExpression ?: it }
        .toList()
        .reversed()

fun KtCallExpression.getContainingValueArgument(expression: KtExpression): KtValueArgument? {
    fun KtElement.deparenthesizeStructurally(): KtElement? {
        val deparenthesized = if (this is KtExpression) KtPsiUtil.deparenthesizeOnce(this) else this
        return when {
            deparenthesized != this -> deparenthesized
            this is KtLambdaExpression -> this.functionLiteral
            this is KtFunctionLiteral -> this.bodyExpression
            else -> null
        }
    }

    for (valueArgument in valueArguments) {
        val argumentExpression = valueArgument.getArgumentExpression() ?: continue
        val candidates = generateSequence<KtElement>(argumentExpression) { it.deparenthesizeStructurally() }
        if (expression in candidates) {
            return valueArgument
        }
    }

    return null
}

fun KtClass.mustHaveNonEmptyPrimaryConstructor(): Boolean =
    isData() || isInlineOrValue()

fun KtClass.mustHaveOnlyPropertiesInPrimaryConstructor(): Boolean =
    isData() || isAnnotation() || isInlineOrValue()

fun KtClass.mustHaveOnlyValPropertiesInPrimaryConstructor(): Boolean =
    isAnnotation() || isInlineOrValue()

fun KtClass.isInlineOrValue(): Boolean =
    isInline() || isValue()

fun KtModifierListOwner.hasInlineModifier(): Boolean =
    hasModifier(KtTokens.INLINE_KEYWORD)

fun KtPrimaryConstructor.mustHaveValOrVar(): Boolean =
    containingClass()?.mustHaveOnlyPropertiesInPrimaryConstructor() ?: false

fun PsiElement.childrenDfsSequence(): Sequence<PsiElement> =
    sequence {
        suspend fun SequenceScope<PsiElement>.visit(element: PsiElement) {
            element.children.forEach { visit(it) }
            yield(element)
        }
        visit(this@childrenDfsSequence)
    }

fun ValueArgument.findSingleLiteralStringTemplateText(): String? {
    return getArgumentExpression()
        ?.safeAs<KtStringTemplateExpression>()
        ?.entries
        ?.singleOrNull()
        ?.safeAs<KtLiteralStringTemplateEntry>()
        ?.text
}

fun PsiElement.isInsideAnnotationEntryArgumentList(): Boolean = parentOfType<KtValueArgumentList>()?.parent is KtAnnotationEntry

fun KtExpression.unwrapIfLabeled(): KtExpression {
    var statement = this
    while (true) {
        statement = statement.parent as? KtLabeledExpression ?: return statement
    }
}

fun KtExpression.previousStatement(): KtExpression? {
    val statement = unwrapIfLabeled()
    if (statement.parent !is KtBlockExpression) return null
    return statement.siblings(forward = false, withItself = false).firstIsInstanceOrNull()
}

fun getCallElement(argument: KtValueArgument): KtCallElement? {
    return if (argument is KtLambdaArgument) {
        argument.parent as? KtCallElement
    } else {
        argument.parents.match(KtValueArgumentList::class, last = KtCallElement::class)
    }
}