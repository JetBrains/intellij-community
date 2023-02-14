// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinPsiModificationUtils")

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry

inline fun <reified T : PsiElement> T.copied(): T {
    return copy() as T
}

inline fun <reified T : PsiElement> PsiElement.replaced(newElement: T): T {
    if (this == newElement) {
        return newElement
    }

    return when (val result = replace(newElement)) {
        is T -> result
        else -> (result as KtParenthesizedExpression).expression as T
    }
}

fun KtBlockStringTemplateEntry.dropCurlyBracketsIfPossible(): KtStringTemplateEntryWithExpression {
    return if (canDropCurlyBrackets()) dropCurlyBrackets() else this
}

@ApiStatus.Internal
fun KtBlockStringTemplateEntry.canDropCurlyBrackets(): Boolean {
    val expression = this.expression
    return (expression is KtNameReferenceExpression || (expression is KtThisExpression && expression.labelQualifier == null))
            && canPlaceAfterSimpleNameEntry(nextSibling)
}

@ApiStatus.Internal
fun KtBlockStringTemplateEntry.dropCurlyBrackets(): KtSimpleNameStringTemplateEntry {
    val name = when (expression) {
        is KtThisExpression -> KtTokens.THIS_KEYWORD.value
        else -> (expression as KtNameReferenceExpression).getReferencedNameElement().text
    }

    val newEntry = KtPsiFactory(project).createSimpleNameStringTemplateEntry(name)
    return replaced(newEntry)
}

fun KtExpression.dropEnclosingParenthesesIfPossible(): KtExpression {
    val innermostExpression = this
    var current = innermostExpression

    while (true) {
        val parent = current.parent as? KtParenthesizedExpression ?: break
        if (!KtPsiUtil.areParenthesesUseless(parent)) break
        current = parent
    }
    return current.replaced(innermostExpression)
}

fun String.unquoteKotlinIdentifier(): String = KtPsiUtil.unquoteIdentifier(this)