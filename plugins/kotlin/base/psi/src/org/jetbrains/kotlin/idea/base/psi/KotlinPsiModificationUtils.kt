// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinPsiModificationUtils")

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry
import org.jetbrains.kotlin.resolve.calls.util.getValueArgumentsInParentheses

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

/**
 * Deletes this single PSI element from the PSI tree without removing other elements.
 * This method is mostly used as a substitute for [PsiElement.delete] because [PsiElement.delete] can delete more than the element it is
 * called on. When for example calling it on a [KtClassOrObject], [PsiElement.delete] will delete the class or object but when this class
 * or object is the only declaration in the file, it will also delete the file. On the contrary [PsiElement.deleteSingle] will only delete
 * the class or object here, but not the file.
 */
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

fun KtLambdaArgument.moveInsideParenthesesAndReplaceWith(
    replacement: KtExpression,
    functionLiteralArgumentName: Name?,
    withNameCheck: Boolean = true,
): KtCallExpression {
    val oldCallExpression = parent as KtCallExpression
    val newCallExpression = oldCallExpression.copy() as KtCallExpression

    val psiFactory = KtPsiFactory(project)

    val argument =
        if (withNameCheck && shouldLambdaParameterBeNamed(newCallExpression.getValueArgumentsInParentheses(), oldCallExpression)) {
            psiFactory.createArgument(replacement, functionLiteralArgumentName)
        } else {
            psiFactory.createArgument(replacement)
        }

    val functionLiteralArgument = newCallExpression.lambdaArguments.firstOrNull()!!
    val valueArgumentList = newCallExpression.valueArgumentList ?: psiFactory.createCallArguments("()")

    valueArgumentList.addArgument(argument)

    (functionLiteralArgument.prevSibling as? PsiWhiteSpace)?.delete()
    if (newCallExpression.valueArgumentList != null) {
        functionLiteralArgument.delete()
    } else {
        functionLiteralArgument.replace(valueArgumentList)
    }
    return oldCallExpression.replace(newCallExpression) as KtCallExpression
}

private fun shouldLambdaParameterBeNamed(args: List<ValueArgument>, callExpr: KtCallExpression): Boolean {
    if (args.any { it.isNamed() }) return true
    val callee = (callExpr.calleeExpression?.mainReference?.resolve() as? KtFunction) ?: return false
    return if (callee.valueParameters.any { it.isVarArg }) true else callee.valueParameters.size - 1 > args.size
}