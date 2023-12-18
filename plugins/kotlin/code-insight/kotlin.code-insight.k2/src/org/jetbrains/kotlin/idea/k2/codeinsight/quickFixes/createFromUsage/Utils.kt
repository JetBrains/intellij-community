// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.types.JvmType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.caches.resolve.KtFileClassProviderImpl
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.types.Variance

fun PsiElement.isPartOfImportDirectiveOrAnnotation() = PsiTreeUtil.getParentOfType(
    this,
    KtTypeReference::class.java, KtAnnotationEntry::class.java, KtImportDirective::class.java
) != null

fun KtExpression.getReceiverExpression(): KtExpression? {
    val nameExpression = this as? KtSimpleNameExpression ?: return null
    return nameExpression.getReceiverExpression()
}

fun KtModifierList?.hasAbstractModifier() = this?.hasModifier(KtTokens.ABSTRACT_KEYWORD) == true

context (KtAnalysisSession)
internal fun KtType.hasAbstractDeclaration(): Boolean {
    val classSymbol = expandedClassSymbol ?: return false
    if (classSymbol.classKind == KtClassKind.INTERFACE) return true
    val declaration = classSymbol.psi as? KtDeclaration ?: return false
    return declaration.modifierList.hasAbstractModifier()
}

context (KtAnalysisSession)
internal fun KtType.canRefactor() = expandedClassSymbol?.psi?.canRefactorElement() == true

context (KtAnalysisSession)
internal fun KtExpression.resolveExpression(): KtSymbol? {
    mainReference?.resolveToSymbol()?.let { return it }
    val call = resolveCall()?.calls?.singleOrNull() ?: return null
    return if (call is KtCallableMemberCall<*, *>) call.symbol else null
}

context (KtAnalysisSession)
internal fun KtType.convertToClass(): KtClass? = expandedClassSymbol?.psi as? KtClass

context (KtAnalysisSession)
internal fun KtType.isInterface(): Boolean {
    val classSymbol = expandedClassSymbol ?: return false
    return classSymbol.classKind == KtClassKind.INTERFACE
}

context (KtAnalysisSession)
internal fun KtElement.getExpectedPsiType(): PsiType? =
    getExpectedType()?.asPsiType(this, allowErrorTypes = false)

context (KtAnalysisSession)
internal fun KtExpression.getClassOfExpressionType(): KtClassOrObject? = when (val symbol = resolveExpression()) {
    is KtCallableSymbol -> symbol.returnType.expandedClassSymbol // When the receiver is a function call or access to a variable
    is KtClassLikeSymbol -> symbol // When the receiver is an object
    else -> getKtType()?.expandedClassSymbol
}?.psi as? KtClassOrObject

internal data class ParameterInfo(val name: String, val type: JvmType?)

context (KtAnalysisSession)
internal fun KtValueArgument.getExpectedParameterInfo(): ParameterInfo {
    val parameterNameAsString = getArgumentName()?.asName?.asString()
    val argumentExpression = getArgumentExpression()
    val expectedArgumentType = argumentExpression?.getKtType()
    val parameterName = parameterNameAsString ?: expectedArgumentType?.let { NAME_SUGGESTER.suggestTypeNames(it).first() }
    val parameterType = expectedArgumentType?.asPsiType(argumentExpression, allowErrorTypes = false)
    return ParameterInfo(parameterName ?: "x", parameterType)
}

context (KtAnalysisSession)
internal fun CreateMethodRequest.getRenderedType(context: KtElement) = returnType.singleOrNull()?.let { returnType ->
    val psiReturnType = returnType.theType as? PsiType
    psiReturnType?.asKtType(context)?.render(position = Variance.INVARIANT)
}

context (KtAnalysisSession)
internal fun KtSimpleNameExpression.getReceiverOrContainerClass(): JvmClass? {
    getReceiverExpression()?.getClassOfExpressionType()?.toLightClass()?.let { return it }
    return getContainerClass()
}

internal fun KtElement.getContainerClass(): JvmClass? {
    val containingClass = getNonStrictParentOfType<KtClassOrObject>()
    return containingClass?.toLightClass() ?: getContainingFileAsJvmClass()
}
private fun KtElement.getContainingFileAsJvmClass(): JvmClass? =
    containingKtFile.findFacadeClass() ?: KtFileClassProviderImpl(project).getFileClasses(containingKtFile).firstOrNull()

private val NAME_SUGGESTER = KotlinNameSuggester()