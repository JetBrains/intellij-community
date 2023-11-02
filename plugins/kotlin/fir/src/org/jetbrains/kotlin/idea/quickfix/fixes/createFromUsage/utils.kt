// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes.createFromUsage

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression

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
internal fun KtExpression.renderExpectedType(): String? = getExpectedType()?.renderBasedOnClassId()

internal fun KtExpression.getClassOfType(): KtClassOrObject? = analyze(this) {
    getKtType()?.expandedClassSymbol?.psi as? KtClassOrObject
}

context (KtAnalysisSession)
private fun KtType.renderBasedOnClassId(): String? = expandedClassSymbol?.classIdIfNonLocal?.asFqNameString()

context (KtAnalysisSession)
internal fun KtCallExpression.renderParameters(): String = valueArguments.joinToString { it.renderExpectedParameter() }

context (KtAnalysisSession)
private fun KtValueArgument.renderExpectedParameter(): String {
    val parameterNameAsString = getArgumentName()?.asName?.asString()
    val argumentExpression = getArgumentExpression()
    val expectedArgumentType = argumentExpression?.getKtType()
    val parameterName = parameterNameAsString ?: expectedArgumentType?.let { NAME_SUGGESTER.suggestTypeNames(it).first() }
    val parameterTypeAsString = expectedArgumentType?.renderBasedOnClassId() ?: "Any"
    return "${parameterName ?: "x"}: $parameterTypeAsString"
}

private val NAME_SUGGESTER = KotlinNameSuggester()