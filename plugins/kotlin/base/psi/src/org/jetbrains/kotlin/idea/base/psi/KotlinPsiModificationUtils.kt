// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinPsiModificationUtils")
@file:OptIn(org.jetbrains.kotlin.psi.KtNonPublicApi::class)
@file:Suppress("unused")

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
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCommonFile
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDoubleColonExpression
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFileAnnotationList
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiMutationService
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.createExpressionByPattern
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
    val body = getOrCreateClassBody()
    val anchor = PsiTreeUtil.skipSiblingsBackward(body.rBrace ?: body.lastChild!!, PsiWhiteSpace::class.java)
    val newDeclaration =
        if (anchor?.nextSibling is PsiErrorElement)
            body.addBefore(declaration, anchor)
        else
            body.addAfter(declaration, anchor)

    return newDeclaration as T
}

fun KtTypeParameterListOwner.addTypeParameter(typeParameter: KtTypeParameter): KtTypeParameter? {
    typeParameterList?.let { return it.appendTypeParameter(typeParameter) }

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

    valueArgumentList.appendValueArgument(argument)

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
    return callee.valueParameters.any { it.isVarArg } || callee.valueParameters.size - 1 > args.size
}

fun replaceSamConstructorCall(callExpression: KtCallExpression): KtLambdaExpression {
    val functionalArgument = callExpression.samConstructorValueArgument?.getArgumentExpression()
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

/**
 * Adds [superTypeListEntry] to this declaration's super type list.
 */
fun KtClassOrObject.addSuperType(superTypeListEntry: KtSuperTypeListEntry): KtSuperTypeListEntry {
    return KtPsiMutationService.getInstance().addSuperType(this, superTypeListEntry)
}

/**
 * Adds [superTypeListEntry] to this super type list.
 */
fun KtSuperTypeList.addSuperType(superTypeListEntry: KtSuperTypeListEntry): KtSuperTypeListEntry {
    return KtPsiMutationService.getInstance().addSuperType(this, superTypeListEntry)
}

/**
 * Removes [superTypeListEntry] from this declaration's super type list.
 */
fun KtClassOrObject.removeSuperType(superTypeListEntry: KtSuperTypeListEntry) {
    KtPsiMutationService.getInstance().removeSuperType(this, superTypeListEntry)
}

/**
 * Removes [superTypeListEntry] from this super type list.
 */
fun KtSuperTypeList.removeSuperType(superTypeListEntry: KtSuperTypeListEntry) {
    KtPsiMutationService.getInstance().removeSuperType(this, superTypeListEntry)
}

/**
 * Adds [declaration] to this declaration's body, creating a body when needed.
 */
fun <T : KtDeclaration> KtClassOrObject.addMemberDeclaration(declaration: T): T {
    return KtPsiMutationService.getInstance().addMemberDeclaration(this, declaration)
}

/**
 * Adds [declaration] after [anchor] in this declaration's body, or appends it when [anchor] is `null`.
 */
fun <T : KtDeclaration> KtClassOrObject.addMemberDeclarationAfter(declaration: T, anchor: PsiElement?): T {
    return KtPsiMutationService.getInstance().addMemberDeclarationAfter(this, declaration, anchor)
}

/**
 * Adds [declaration] before [anchor] in this declaration's body, or prepends it when [anchor] is `null`.
 */
fun <T : KtDeclaration> KtClassOrObject.addMemberDeclarationBefore(declaration: T, anchor: PsiElement?): T {
    return KtPsiMutationService.getInstance().addMemberDeclarationBefore(this, declaration, anchor)
}

/**
 * Returns the existing body for this declaration, or creates one if missing.
 */
fun KtClassOrObject.getOrCreateClassBody(): KtClassBody {
    return KtPsiMutationService.getInstance().getOrCreateClassBody(this)
}

/**
 * Adds a semicolon to this enum entry, reusing an existing sibling semicolon when possible.
 */
fun KtEnumEntry.addEnumEntrySemicolon(): PsiElement {
    return KtPsiMutationService.getInstance().addEnumEntrySemicolon(this)
}

/**
 * Returns the existing primary constructor for this class, or creates one if missing.
 */
fun KtClass.getOrCreatePrimaryConstructor(): KtPrimaryConstructor {
    return KtPsiMutationService.getInstance().getOrCreatePrimaryConstructor(this)
}

/**
 * Returns the existing primary constructor parameter list for this class, or creates one if missing.
 */
fun KtClass.getOrCreatePrimaryConstructorParameterList(): KtParameterList {
    return KtPsiMutationService.getInstance().getOrCreatePrimaryConstructorParameterList(this)
}

/**
 * Replaces this file's package name, adding a package directive when needed.
 */
@Suppress("DEPRECATION")
fun KtCommonFile.setPackageFqName(fqName: FqName) {
    KtPsiMutationService.getInstance().setPackageFqName(this, fqName)
}

/**
 * Replaces this package directive's package name.
 */
fun KtPackageDirective.setPackageFqName(fqName: FqName) {
    KtPsiMutationService.getInstance().setPackageFqName(this, fqName)
}

/**
 * Replaces this file's file annotation list with [annotationList], or adds it when missing.
 */
fun KtFile.replaceFileAnnotationList(annotationList: KtFileAnnotationList): KtFileAnnotationList {
    return KtPsiMutationService.getInstance().replaceFileAnnotationList(this, annotationList)
}

/**
 * Replaces this declaration's modifier list with [newModifierList], or adds it when missing.
 */
fun KtModifierListOwner.setModifierList(newModifierList: KtModifierList) {
    KtPsiMutationService.getInstance().setModifierList(this, newModifierList)
}

/**
 * Replaces this declaration's modifier list with [modifierList], adds it when missing, or removes it when [modifierList] is `null`.
 */
fun KtModifierListOwner.replaceModifierList(modifierList: KtModifierList?): KtModifierList? {
    return KtPsiMutationService.getInstance().replaceModifierList(this, modifierList)
}

/**
 * Adds [modifier] to this declaration's modifier list.
 */
fun KtModifierListOwner.addModifierKeyword(modifier: KtModifierKeywordToken) {
    KtPsiMutationService.getInstance().addModifierKeyword(this, modifier)
}

/**
 * Adds [modifier] to this primary constructor's modifier list.
 */
fun KtPrimaryConstructor.addModifierKeyword(modifier: KtModifierKeywordToken) {
    KtPsiMutationService.getInstance().addModifierKeyword(this, modifier)
}

/**
 * Removes [modifier] from this declaration's modifier list.
 */
fun KtModifierListOwner.removeModifierKeyword(modifier: KtModifierKeywordToken) {
    KtPsiMutationService.getInstance().removeModifierKeyword(this, modifier)
}

/**
 * Removes [modifier] from this primary constructor's modifier list.
 */
fun KtPrimaryConstructor.removeModifierKeyword(modifier: KtModifierKeywordToken) {
    KtPsiMutationService.getInstance().removeModifierKeyword(this, modifier)
}

/**
 * Adds [annotationEntry] to this declaration's modifier list.
 */
fun KtModifierListOwner.addAnnotation(annotationEntry: KtAnnotationEntry): KtAnnotationEntry {
    return KtPsiMutationService.getInstance().addAnnotation(this, annotationEntry)
}

/**
 * Adds [annotationEntry] to this primary constructor's modifier list.
 */
fun KtPrimaryConstructor.addAnnotation(annotationEntry: KtAnnotationEntry): KtAnnotationEntry {
    return KtPsiMutationService.getInstance().addAnnotation(this, annotationEntry)
}

/**
 * Removes [entry] from this annotation.
 */
fun KtAnnotation.removeAnnotationEntry(entry: KtAnnotationEntry) {
    KtPsiMutationService.getInstance().removeAnnotationEntry(this, entry)
}

/**
 * Removes this primary constructor's redundant `constructor` keyword.
 */
fun KtPrimaryConstructor.removeRedundantConstructorKeyword() {
    KtPsiMutationService.getInstance().removeRedundantConstructorKeyword(this)
}

/**
 * Replaces this function's return type reference, adds it if missing, or removes it when [typeRef] is `null`.
 */
fun KtNamedFunction.setFunctionTypeReference(typeRef: KtTypeReference?): KtTypeReference? {
    return KtPsiMutationService.getInstance().setFunctionTypeReference(this, typeRef)
}

/**
 * Replaces this property's type reference, adds it if missing, or removes it when [typeRef] is `null`.
 */
fun KtProperty.setPropertyTypeReference(typeRef: KtTypeReference?): KtTypeReference? {
    return KtPsiMutationService.getInstance().setPropertyTypeReference(this, typeRef)
}

/**
 * Replaces this property's initializer, adds it if missing, or removes it when [initializer] is `null`.
 */
fun KtProperty.setPropertyInitializer(initializer: KtExpression?): KtExpression? {
    return KtPsiMutationService.getInstance().setPropertyInitializer(this, initializer)
}

/**
 * Replaces this parameter's type reference, adds it if missing, or removes it when [typeRef] is `null`.
 */
fun KtParameter.setParameterTypeReference(typeRef: KtTypeReference?): KtTypeReference? {
    return KtPsiMutationService.getInstance().setParameterTypeReference(this, typeRef)
}

/**
 * Replaces this destructuring entry's type reference, adds it if missing, or removes it when [typeRef] is `null`.
 */
fun KtDestructuringDeclarationEntry.setDestructuringDeclarationEntryTypeReference(
    typeRef: KtTypeReference?,
): KtTypeReference? {
    return KtPsiMutationService.getInstance().setDestructuringDeclarationEntryTypeReference(this, typeRef)
}

/**
 * Replaces this callable's explicit return type reference, adds it if missing, or removes it when [typeRef] is `null`.
 */
fun KtCallableDeclaration.setCallableTypeReference(
    addAfter: PsiElement?,
    typeRef: KtTypeReference?,
): KtTypeReference? {
    return KtPsiMutationService.getInstance().setCallableTypeReference(this, addAfter, typeRef)
}

/**
 * Replaces this callable's receiver type reference, adds it if missing, or removes it when [typeRef] is `null`.
 */
fun KtCallableDeclaration.setCallableReceiverTypeReference(typeRef: KtTypeReference?): KtTypeReference? {
    return KtPsiMutationService.getInstance().setCallableReceiverTypeReference(this, typeRef)
}

/**
 * Replaces this function type's receiver type reference, adds it if missing, or removes it when [typeRef] is `null`.
 */
fun KtFunctionType.setFunctionTypeReceiverTypeReference(typeRef: KtTypeReference?): KtTypeReference? {
    return KtPsiMutationService.getInstance().setFunctionTypeReceiverTypeReference(this, typeRef)
}

/**
 * Replaces this type parameter's extends bound, adds it if missing, or removes it when [typeReference] is `null`.
 */
fun KtTypeParameter.setTypeParameterExtendsBound(typeReference: KtTypeReference?): KtTypeReference? {
    return KtPsiMutationService.getInstance().setTypeParameterExtendsBound(this, typeReference)
}

/**
 * Replaces this double-colon expression's receiver expression, or adds it if missing.
 */
fun KtDoubleColonExpression.setDoubleColonReceiverExpression(newReceiverExpression: KtExpression) {
    KtPsiMutationService.getInstance().setDoubleColonReceiverExpression(this, newReceiverExpression)
}

/**
 * Removes this user type's qualifier, keeping the referenced name intact.
 */
fun KtUserType.removeQualifier() {
    KtPsiMutationService.getInstance().removeQualifier(this)
}

/**
 * Replaces this constructor's implicit delegation call with an explicit `this()` or `super()` call.
 */
fun KtSecondaryConstructor.convertImplicitDelegationCallToExplicit(isThis: Boolean): KtConstructorDelegationCall {
    return KtPsiMutationService.getInstance().convertImplicitDelegationCallToExplicit(this, isThis)
}

/**
 * Adds [parameter] to this parameter list.
 */
fun KtParameterList.appendParameter(parameter: KtParameter): KtParameter {
    return KtPsiMutationService.getInstance().appendParameter(this, parameter)
}

/**
 * Adds [parameter] before [anchor] in this parameter list.
 */
fun KtParameterList.insertParameterBefore(parameter: KtParameter, anchor: KtParameter?): KtParameter {
    return KtPsiMutationService.getInstance().insertParameterBefore(this, parameter, anchor)
}

/**
 * Adds [parameter] after [anchor] in this parameter list.
 */
fun KtParameterList.insertParameterAfter(parameter: KtParameter, anchor: KtParameter?): KtParameter {
    return KtPsiMutationService.getInstance().insertParameterAfter(this, parameter, anchor)
}

/**
 * Removes [parameter] from this parameter list.
 */
fun KtParameterList.deleteParameter(parameter: KtParameter) {
    KtPsiMutationService.getInstance().deleteParameter(this, parameter)
}

/**
 * Removes the parameter at [index] from this parameter list.
 */
fun KtParameterList.deleteParameter(index: Int) {
    KtPsiMutationService.getInstance().deleteParameter(this, index)
}

/**
 * Adds [typeParameter] to this type parameter list.
 */
fun KtTypeParameterList.appendTypeParameter(typeParameter: KtTypeParameter): KtTypeParameter {
    return KtPsiMutationService.getInstance().appendTypeParameter(this, typeParameter)
}

/**
 * Adds [typeArgument] to this type argument list.
 */
fun KtTypeArgumentList.appendTypeArgument(typeArgument: KtTypeProjection): KtTypeProjection {
    return KtPsiMutationService.getInstance().appendTypeArgument(this, typeArgument)
}

/**
 * Adds [argument] to this value argument list.
 */
fun KtValueArgumentList.appendValueArgument(argument: KtValueArgument): KtValueArgument {
    return KtPsiMutationService.getInstance().appendValueArgument(this, argument)
}

/**
 * Adds [argument] after [anchor] in this value argument list.
 */
fun KtValueArgumentList.insertValueArgumentAfter(argument: KtValueArgument, anchor: KtValueArgument?): KtValueArgument {
    return KtPsiMutationService.getInstance().insertValueArgumentAfter(this, argument, anchor)
}

/**
 * Adds [argument] before [anchor] in this value argument list.
 */
fun KtValueArgumentList.insertValueArgumentBefore(argument: KtValueArgument, anchor: KtValueArgument?): KtValueArgument {
    return KtPsiMutationService.getInstance().insertValueArgumentBefore(this, argument, anchor)
}

/**
 * Removes [argument] from this value argument list.
 */
fun KtValueArgumentList.deleteValueArgument(argument: KtValueArgument) {
    KtPsiMutationService.getInstance().deleteValueArgument(this, argument)
}

/**
 * Removes the value argument at [index] from this value argument list.
 */
fun KtValueArgumentList.deleteValueArgument(index: Int) {
    KtPsiMutationService.getInstance().deleteValueArgument(this, index)
}

/**
 * Returns this function literal's existing value parameter list, or creates one together with the arrow token.
 */
fun KtFunctionLiteral.getOrCreateFunctionLiteralParameterList(): KtParameterList {
    return KtPsiMutationService.getInstance().getOrCreateFunctionLiteralParameterList(this)
}

/**
 * Returns this call expression's existing value argument list, or creates one.
 */
fun KtCallExpression.getOrCreateCallValueArgumentList(): KtValueArgumentList {
    return KtPsiMutationService.getInstance().getOrCreateCallValueArgumentList(this)
}

/**
 * Adds [typeArgument] to this call expression, creating the type argument list when needed.
 */
fun KtCallExpression.appendTypeArgument(typeArgument: KtTypeProjection) {
    KtPsiMutationService.getInstance().appendTypeArgument(this, typeArgument)
}

/**
 * Replaces this element with [newElement] on the AST level.
 */
fun PsiElement.astReplace(newElement: PsiElement) {
    KtPsiMutationService.getInstance().astReplace(this, newElement)
}

/**
 * Replaces this expression with [newElement], adding parentheses or string-template braces when needed.
 */
fun KtExpression.replaceExpression(
    newElement: PsiElement,
    reformat: Boolean = true,
    rawReplaceHandler: (PsiElement) -> PsiElement,
): PsiElement {
    return KtPsiMutationService.getInstance().replaceExpression(this, newElement, reformat, rawReplaceHandler)
}