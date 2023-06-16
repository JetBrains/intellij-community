// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinPsiModificationUtils")

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
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

fun PsiElement.deleteSingle() {
    CodeEditUtil.removeChild(parent?.node ?: return, node ?: return)
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

fun KtClass.getOrCreateCompanionObject(): KtObjectDeclaration {
    companionObjects.firstOrNull()?.let { return it }
    return appendDeclaration(KtPsiFactory(project).createCompanionObject())
}

inline fun <reified T : KtDeclaration> KtClass.appendDeclaration(declaration: T): T {
    val body = getOrCreateBody()
    val anchor = PsiTreeUtil.skipSiblingsBackward(body.rBrace ?: body.lastChild!!, PsiWhiteSpace::class.java)
    val newDeclaration =
        if (anchor?.nextSibling is PsiErrorElement)
            body.addBefore(declaration, anchor)
        else
            body.addAfter(declaration, anchor)

    return newDeclaration as T
}

fun KtTypeParameterListOwner.addTypeParameter(typeParameter: KtTypeParameter): KtTypeParameter? {
    typeParameterList?.let { return it.addParameter(typeParameter) }

    val list = KtPsiFactory(project).createTypeParameterList("<X>")
    list.parameters[0].replace(typeParameter)
    val leftAnchor = when (this) {
        is KtClass -> nameIdentifier
        is KtNamedFunction -> funKeyword
        is KtProperty -> valOrVarKeyword
        is KtTypeAlias -> nameIdentifier
        else -> null
    } ?: return null
    return (addAfter(list, leftAnchor) as KtTypeParameterList).parameters.first()
}

fun KtParameter.setDefaultValue(newDefaultValue: KtExpression): PsiElement {
    defaultValue?.let { return it.replaced(newDefaultValue) }

    val psiFactory = KtPsiFactory(project)
    val eq = equalsToken ?: add(psiFactory.createEQ())
    return addAfter(newDefaultValue, eq) as KtExpression
}