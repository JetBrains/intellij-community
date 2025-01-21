// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinPsiModificationUtils")

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.hasBody

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

    val newEntry = (parent as? KtStringTemplateExpression)?.interpolationPrefix?.let { interpolationPrefix ->
        KtPsiFactory(project).createMultiDollarSimpleNameStringTemplateEntry(name, interpolationPrefix.textLength)
    } ?: KtPsiFactory(project).createSimpleNameStringTemplateEntry(name)

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

/**
 * Moves the lambda argument inside parentheses and replaces it with the specified replacement expression.
 *
 * @param replacement The replacement expression to be used.
 * @param lambdaArgumentName The name of the lambda argument; use `null` if no name is needed.
 * @return The modified `KtCallExpression` with the lambda argument moved inside parentheses and replaced with
 * the specified replacement expression.
 */
fun KtLambdaArgument.moveInsideParenthesesAndReplaceWith(
    replacement: KtExpression,
    lambdaArgumentName: Name?,
): KtCallExpression {
    val oldCallExpression = parent as KtCallExpression
    val newCallExpression = oldCallExpression.copy() as KtCallExpression

    val psiFactory = KtPsiFactory(project)
    val argument = psiFactory.createArgument(replacement, lambdaArgumentName)

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

/**
 * Returns `true` if the lambda argument should be named, `false` otherwise.
 */
fun shouldLambdaParameterBeNamed(argument: KtLambdaArgument): Boolean {
    val callExpression = argument.parent as KtCallExpression
    val args = callExpression.valueArguments.filter { it !is KtLambdaArgument }
    if (args.any { it.isNamed() }) return true
    val callee = (callExpression.calleeExpression?.mainReference?.resolve() as? KtFunction) ?: return false
    return if (callee.valueParameters.any { it.isVarArg }) true else callee.valueParameters.size - 1 > args.size
}

fun replaceSamConstructorCall(callExpression: KtCallExpression): KtLambdaExpression {
    val functionalArgument = callExpression.getSamConstructorValueArgument()?.getArgumentExpression()
        ?: throw AssertionError("SAM constructor should have a FunctionLiteralExpression as single argument: ${callExpression.getElementTextWithContext()}")
    val ktExpression = callExpression.getQualifiedExpressionForSelectorOrThis()
    return runWriteActionIfPhysical(ktExpression) { ktExpression.replace(functionalArgument) as KtLambdaExpression }
}

/**
 * @return the expression which was actually inserted in the tree
 */
fun KtExpression.prependDotQualifiedReceiver(receiver: KtExpression, factory: KtPsiFactory): KtExpression {
    val dotQualified = factory.createExpressionByPattern("$0.$1", receiver, this)
    return this.replaced(dotQualified)
}

/**
 * @return the expression which was actually inserted in the tree
 */
fun KtExpression.appendDotQualifiedSelector(selector: KtExpression, factory: KtPsiFactory): KtExpression {
    val dotQualified = factory.createExpressionByPattern("$0.$1", this, selector)
    return this.replaced(dotQualified)
}

fun KtSecondaryConstructor.getOrCreateBody(): KtBlockExpression {
    bodyExpression?.let { return it }

    val delegationCall = getDelegationCall()
    val anchor = if (delegationCall.isImplicit) valueParameterList else delegationCall
    val newBody = KtPsiFactory(project).createEmptyBody()
    return addAfter(newBody, anchor) as KtBlockExpression
}

fun KtDeclaration.predictImplicitModality(): KtModifierKeywordToken {
    if (this is KtClassOrObject) {
        if (this is KtClass && this.isInterface()) return KtTokens.ABSTRACT_KEYWORD
        return KtTokens.FINAL_KEYWORD
    }
    val klass = containingClassOrObject ?: return KtTokens.FINAL_KEYWORD
    if (hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
        if (klass.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
            klass.hasModifier(KtTokens.OPEN_KEYWORD) ||
            klass.hasModifier(KtTokens.SEALED_KEYWORD)
        ) {
            return KtTokens.OPEN_KEYWORD
        }
    }
    if (klass is KtClass && klass.isInterface() && !hasModifier(KtTokens.PRIVATE_KEYWORD)) {
        return if (hasBody()) KtTokens.OPEN_KEYWORD else KtTokens.ABSTRACT_KEYWORD
    }
    return KtTokens.FINAL_KEYWORD
}


fun KtCallExpression.getOrCreateValueArgumentList(): KtValueArgumentList {
    valueArgumentList?.let { return it }
    val newList = KtPsiFactory(project).createCallArguments("()")
    return addAfter(newList, typeArgumentList ?: calleeExpression) as KtValueArgumentList
}