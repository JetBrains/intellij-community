// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.types.JvmType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtDefinitelyNotNullTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtFlexibleTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtTypeProjectionRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.caches.resolve.KtFileClassProviderImpl
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression

fun PsiElement.isPartOfImportDirectiveOrAnnotation() = PsiTreeUtil.getParentOfType(
    this,
    KtTypeReference::class.java, KtAnnotationEntry::class.java, KtImportDirective::class.java
) != null

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
internal fun KtElement.getExpectedJvmType(): JvmType? = getExpectedType()?.let { JvmTypeWrapperForKtType(it) }

context (KtAnalysisSession)
internal fun KtExpression.getClassOfExpressionType(): KtClassOrObject? = when (val symbol = resolveExpression()) {
    is KtCallableSymbol -> symbol.returnType.expandedClassSymbol // When the receiver is a function call or access to a variable
    is KtClassLikeSymbol -> symbol // When the receiver is an object
    else -> getKtType()?.expandedClassSymbol
}?.psi as? KtClassOrObject

internal data class ParameterInfo(val nameCandidates: MutableList<String>, val type: JvmType?)

context (KtAnalysisSession)
internal fun KtValueArgument.getExpectedParameterInfo(parameterIndex: Int): ParameterInfo {
    val parameterNameAsString = getArgumentName()?.asName?.asString()
    val argumentExpression = getArgumentExpression()
    val expectedArgumentType = argumentExpression?.getKtType()
    val parameterName = parameterNameAsString?.let { sequenceOf(it) } ?: expectedArgumentType?.let { NAME_SUGGESTER.suggestTypeNames(it) }
    val parameterType = expectedArgumentType?.let { JvmTypeWrapperForKtType(it) }
    return ParameterInfo(parameterName?.toMutableList() ?: mutableListOf("p$parameterIndex"), parameterType)
}

context (KtAnalysisSession)
internal fun KtSimpleNameExpression.getReceiverOrContainerClass(): JvmClass? {
    getReceiverExpression()?.getClassOfExpressionType()?.let { return JvmClassWrapperForKtClass(it) }
    return getContainerClass()
}

internal fun KtElement.getContainerClass(): JvmClass? {
    val containingClass = getNonStrictParentOfType<KtClassOrObject>()
    return containingClass?.let { JvmClassWrapperForKtClass(it) } ?: getContainingFileAsJvmClass()
}

private fun KtElement.getContainingFileAsJvmClass(): JvmClass? =
    containingKtFile.findFacadeClass() ?: KtFileClassProviderImpl(project).getFileClasses(containingKtFile).firstOrNull()

private val NAME_SUGGESTER = KotlinNameSuggester()

val WITH_TYPE_NAMES_FOR_CREATE_ELEMENTS: KtTypeRenderer = KtTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
    // Without this, it will render `kotlin.String!` for `kotlin.String`, which causes a syntax error.
    flexibleTypeRenderer = object : KtFlexibleTypeRenderer {
        context(KtAnalysisSession, KtTypeRenderer)
        override fun renderType(type: KtFlexibleType, printer: PrettyPrinter) {
            renderType(type.lowerBound, printer)
        }
    }
    // Without this, it can render `kotlin.String & kotlin.Any`, which causes a syntax error.
    definitelyNotNullTypeRenderer = object : KtDefinitelyNotNullTypeRenderer {
        context(KtAnalysisSession, KtTypeRenderer)
        override fun renderType(type: KtDefinitelyNotNullType, printer: PrettyPrinter) {
            renderType(type.original, printer)
        }
    }
    // Listing variances will cause a syntax error.
    typeProjectionRenderer = KtTypeProjectionRenderer.WITHOUT_VARIANCE
}

context (KtAnalysisSession)
internal fun JvmType.toKtType(useSitePosition: PsiElement) = when (this) {
    is JvmTypeWrapperForKtType -> ktType
    is PsiType -> if (isValid) asKtType(useSitePosition) else null
    else -> null
}