// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.lang.jvm.actions.expectedParameter
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtDefinitelyNotNullTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtFlexibleTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtTypeProjectionRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.caches.resolve.KtFileClassProviderImpl
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression

fun PsiElement.isPartOfImportDirectiveOrAnnotation(): Boolean = PsiTreeUtil.getParentOfType(
    this,
    KtTypeReference::class.java, KtAnnotationEntry::class.java, KtImportDirective::class.java
) != null

fun KtModifierList?.hasAbstractModifier(): Boolean = this?.hasModifier(KtTokens.ABSTRACT_KEYWORD) == true

context (KtAnalysisSession)
internal fun KtType.hasAbstractDeclaration(): Boolean {
    val classSymbol = expandedClassSymbol ?: return false
    if (classSymbol.classKind == KtClassKind.INTERFACE) return true
    val declaration = classSymbol.psi as? KtDeclaration ?: return false
    return declaration.modifierList.hasAbstractModifier()
}

context (KtAnalysisSession)
internal fun KtType.canRefactor(): Boolean = expandedClassSymbol?.psi?.canRefactorElement() == true

context (KtAnalysisSession)
internal fun KtExpression.resolveExpression(): KtSymbol? {
    mainReference?.resolveToSymbol()?.let { return it }
    val call = resolveCall()?.calls?.singleOrNull() ?: return null
    return if (call is KtCallableMemberCall<*, *>) call.symbol else null
}

context (KtAnalysisSession)
internal fun KtType.convertToClass(): KtClass? = expandedClassSymbol?.psi as? KtClass

context (KtAnalysisSession)
internal fun KtElement.getExpectedKotlinType(): ExpectedKotlinType? {
    var expectedType = getExpectedType()
    if (expectedType == null) {
        val parent = this.parent
        expectedType = when {
            parent is KtPrefixExpression && parent.operationToken == KtTokens.EXCL -> builtinTypes.BOOLEAN
            parent is KtStringTemplateEntryWithExpression -> builtinTypes.STRING
            parent is KtPropertyDelegate -> {
                val variable = parent.parent as KtProperty
                val delegateClassName = if (variable.isVar) "ReadWriteProperty" else "ReadOnlyProperty"
                val ktType = variable.getReturnKtType()
                val symbol = variable.getSymbol() as? KtCallableSymbol
                val parameterType = symbol?.receiverType ?: (variable.getSymbol()
                    .getContainingSymbol() as? KtNamedClassOrObjectSymbol)?.buildSelfClassType() ?: builtinTypes.NULLABLE_ANY
                buildClassType(ClassId.fromString("kotlin/properties/$delegateClassName")) {
                    argument(parameterType)
                    argument(ktType)
                }
            }
            parent is KtNamedFunction && parent.nameIdentifier == null && parent.bodyExpression == this && parent.parent is KtValueArgument -> {
                (parent.getExpectedType() as? KtFunctionalType)?.returnType
            }
            else -> null
        }
    }
    val jvmType = expectedType?.convertToJvmType(this) ?: return null
    return ExpectedKotlinType.createExpectedKotlinType(jvmType, expectedType.nullability)
}

context (KtAnalysisSession)
private fun KtType.convertToJvmType(useSitePosition: PsiElement): JvmType? = asPsiType(useSitePosition, allowErrorTypes = false)

context (KtAnalysisSession)
internal fun KtExpression.getClassOfExpressionType(): PsiElement? = when (val symbol = resolveExpression()) {
    //is KtCallableSymbol -> symbol.returnType.expandedClassSymbol // When the receiver is a function call or access to a variable
    is KtClassLikeSymbol -> symbol // When the receiver is an object
    else -> getKtType()?.expandedClassSymbol
}?.psi

context (KtAnalysisSession)
internal fun KtValueArgument.getExpectedParameterInfo(parameterIndex: Int): ExpectedParameter {
    val parameterNameAsString = getArgumentName()?.asName?.asString()
    val argumentExpression = getArgumentExpression()
    val expectedArgumentType = argumentExpression?.getKtType()
    val parameterNames = parameterNameAsString?.let { sequenceOf(it) } ?: expectedArgumentType?.let { NAME_SUGGESTER.suggestTypeNames(it) }
    val parameterType = expectedArgumentType?.convertToJvmType(argumentExpression)
    val expectedType = if (parameterType == null) ExpectedKotlinType.INVALID_TYPE else ExpectedKotlinType.createExpectedKotlinType(parameterType, expectedArgumentType.nullability)
    val names = parameterNames?.toList()?.toTypedArray() ?: arrayOf("p$parameterIndex")
    return expectedParameter(expectedType, *names)
}

context (KtAnalysisSession)
internal fun KtSimpleNameExpression.getReceiverOrContainerClass(): JvmClass? {
    val receiverExpression = getReceiverExpression()
    return when (val ktClassOrPsiClass = receiverExpression?.getClassOfExpressionType()) {
        is PsiClass -> ktClassOrPsiClass
        is KtClassOrObject -> ktClassOrPsiClass.toLightClass()
        else -> getContainerClass()
    }
}

context (KtAnalysisSession)
internal fun KtSimpleNameExpression.getReceiverOrContainerClassPackageName(): FqName? =
    when (val ktClassOrPsiClass = getReceiverExpression()?.getClassOfExpressionType()) {
        is PsiClass -> ktClassOrPsiClass.getNonStrictParentOfType<PsiPackage>()?.kotlinFqName
        is KtClassOrObject -> ktClassOrPsiClass.classIdIfNonLocal?.packageFqName
        else -> null
    }

private fun KtElement.getContainerClass(): JvmClass? {
    val containingClass = getNonStrictParentOfType<KtClassOrObject>()
    return containingClass?.toLightClass() ?: getContainingFileAsJvmClass()
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
internal fun JvmType.toKtType(useSitePosition: PsiElement): KtType? = when (this) {
    is PsiType -> if (isValid) {
        try {
            asKtType(useSitePosition)
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e

            // Some requests from Java side do not have a type. For example, in `var foo = dep.<caret>foo();`, we cannot guess
            // the type of `foo()`. In this case, the request passes "PsiType:null" whose name is "null" as a text. The analysis
            // API cannot get a KtType from this weird type. We return `Any?` for this case.
            builtinTypes.NULLABLE_ANY
        }
    } else {
        null
    }

    else -> null
}