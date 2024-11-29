// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.Nullability
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.actions.ExpectedTypeWithNullability
import com.intellij.lang.jvm.actions.expectedParameter
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.isAncestor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaDefinitelyNotNullTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFlexibleTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFunctionalTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaTypeProjectionRenderer
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightElement
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

object K2CreateFunctionFromUsageUtil {
    fun PsiElement.isPartOfImportDirectiveOrAnnotation(): Boolean = PsiTreeUtil.getParentOfType(
        this,
        KtTypeReference::class.java, KtAnnotationEntry::class.java, KtImportDirective::class.java
    ) != null

    fun KtModifierList?.hasAbstractModifier(): Boolean = this?.hasModifier(KtTokens.ABSTRACT_KEYWORD) == true

    context (KaSession)
    internal fun KaType.hasAbstractDeclaration(): Boolean {
        val classSymbol = expandedSymbol ?: return false
        if (classSymbol.classKind == KaClassKind.INTERFACE) return true
        val declaration = classSymbol.psi as? KtDeclaration ?: return false
        return declaration.modifierList.hasAbstractModifier()
    }

    context (KaSession)
    internal fun KaType.canRefactor(): Boolean = expandedSymbol?.psi?.canRefactorElement() == true

    context (KaSession)
    fun KtExpression.resolveExpression(): KaSymbol? {
        val reference = mainReference?:(this as? KtThisExpression)?.instanceReference?.mainReference
        reference?.resolveToSymbol()?.let { return it }
        val call = resolveToCall()?.calls?.singleOrNull() ?: return null
        return if (call is KaCallableMemberCall<*, *>) call.symbol else null
    }

    context (KaSession)
    internal fun KaType.convertToClass(): KtClass? = expandedSymbol?.psi as? KtClass

    context (KaSession)
    internal fun KtElement.getExpectedKotlinType(): ExpectedKotlinType? {
        var expectedType = expectedType
        if (expectedType == null) {
            val parent = this.parent
            expectedType = when {
                parent is KtPrefixExpression && parent.operationToken == KtTokens.EXCL -> builtinTypes.boolean
                parent is KtStringTemplateEntryWithExpression -> builtinTypes.string
                parent is KtPropertyDelegate -> {
                    val variable = parent.parent as KtProperty
                    val delegateClassName = if (variable.isVar) "ReadWriteProperty" else "ReadOnlyProperty"
                    val ktType = variable.returnType
                    val symbol = variable.symbol as? KaCallableSymbol
                    val parameterType = symbol?.receiverType ?: (variable.symbol
                        .containingDeclaration as? KaNamedClassSymbol)?.defaultType ?: builtinTypes.nullableAny
                    buildClassType(ClassId.fromString("kotlin/properties/$delegateClassName")) {
                        argument(parameterType)
                        argument(ktType)
                    }
                }
                parent is KtNamedFunction && parent.nameIdentifier == null && parent.bodyExpression == this && parent.parent is KtValueArgument -> {
                    (parent.expectedType as? KaFunctionType)?.returnType
                }
                else -> null
            }
            if (expectedType == null && this is KtExpression) {
                expectedType = getExpectedTypeByFunctionExpressionBody(this)
            }
            if (expectedType == null && this is KtExpression) {
                expectedType = getExpectedTypeByStringTemplateEntry(this)
            }
        }
        if (expectedType == null) return null
        expectedType = makeAccessibleInCreationPlace(expectedType, this) ?: return null
        val jvmType = expectedType.convertToJvmType(this) ?: return null
        return ExpectedKotlinType(expectedType, jvmType)
    }

    // Given: `println("a = ${A().foo()}")`
    // Expected type of `foo()` is `String`
    context (KaSession)
    private fun getExpectedTypeByStringTemplateEntry(expression: KtExpression): KaType? {
        var e:PsiElement = expression
        while (e is KtExpression && e !is KtStringTemplateEntry) {
            val parent = e.parent
            if (parent is KtQualifiedExpression && parent.selectorExpression != e) break
            e = parent
        }
        if (e is KtStringTemplateEntry) {
            return withValidityAssertion { useSiteSession.builtinTypes.string }
        }
        return null
    }

    // Given: `fun f(): T = expression`
    // Expected type of `expression` is `T`
    context (KaSession)
    private fun getExpectedTypeByFunctionExpressionBody(expression: KtExpression): KaType? {
        var e:PsiElement = expression
        while (e is KtExpression && e !is KtFunction) {
            e=e.parent
        }
        if (e is KtFunction && e.bodyBlockExpression == null && e.bodyExpression?.isAncestor(expression) == true) {
            // workaround of the bug when KtFunction.expectedType is always null
            val expectedType = e.expectedType ?:
                e.returnType.let { if (it is KaErrorType) null else it } ?:
                withValidityAssertion { useSiteSession.builtinTypes.any }
            return expectedType
        }
        return null
    }

    context (KaSession)
    @OptIn(KaExperimentalApi::class)
    fun KaType.convertToJvmType(useSitePosition: PsiElement): JvmType? = asPsiType(useSitePosition, allowErrorTypes = false)

    context (KaSession)
    private fun KtExpression.getClassOfExpressionType(): PsiElement? = when (val symbol = resolveExpression()) {
        //is KaCallableSymbol -> symbol.returnType.expandedClassSymbol // When the receiver is a function call or access to a variable
        is KaClassLikeSymbol -> symbol // When the receiver is an object
        else -> expressionType?.expandedSymbol
    }?.psi

    context (KaSession)
    internal fun ValueArgument.getExpectedParameterInfo(defaultParameterName: String, isTheOnlyAnnotationParameter:Boolean): ExpectedParameter {
        val parameterNameAsString = getArgumentName()?.asName?.asString()
        val argumentExpression = getArgumentExpression()
        val expectedArgumentType = argumentExpression?.expressionType
        val parameterNames = parameterNameAsString?.let { sequenceOf(it) } ?: expectedArgumentType?.let { NAME_SUGGESTER.suggestTypeNames(it) }
        val jvmParameterType = expectedArgumentType?.convertToJvmType(argumentExpression)
        val expectedType = if (jvmParameterType == null) ExpectedTypeWithNullability.INVALID_TYPE else ExpectedKotlinType(expectedArgumentType, jvmParameterType)
        val names = parameterNames?.toList() ?: listOf(defaultParameterName)
        val nameArray = (if (isTheOnlyAnnotationParameter && parameterNameAsString==null) listOf("value") + names else names).toTypedArray()
        return expectedParameter(expectedType, *nameArray)
    }

    context (KaSession)
    internal fun KtSimpleNameExpression.getReceiverOrContainerClass(containerPsi: PsiElement?): JvmClass? {
        return when(containerPsi) {
            is PsiClass -> containerPsi
            is KtClass -> containerPsi.getContainerClass()
            is KtClassOrObject -> containerPsi.toLightClass()
            else -> getContainerClass()
        }
    }
    context (KaSession)
    internal fun KtSimpleNameExpression.getReceiverOrContainerPsiElement(): PsiElement? {
        val receiverExpression = getReceiverExpression()
        return when (val ktClassOrPsiClass = receiverExpression?.getClassOfExpressionType()) {
            is PsiClass -> ktClassOrPsiClass
            is KtClassOrObject -> ktClassOrPsiClass
            else -> K2CreateFunctionFromUsageBuilder.computeImplicitReceiverClass(this) ?: getNonStrictParentOfType<KtClassOrObject>()
        }
    }

    context (KaSession)
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

    @KaExperimentalApi
    val WITH_TYPE_NAMES_FOR_CREATE_ELEMENTS: KaTypeRenderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
        // Without this, it will render `kotlin.String!` for `kotlin.String`, which causes a syntax error.
        flexibleTypeRenderer = object : KaFlexibleTypeRenderer {
            override fun renderType(
                analysisSession: KaSession,
                type: KaFlexibleType,
                typeRenderer: KaTypeRenderer,
                printer: PrettyPrinter
            ) {
                typeRenderer.renderType(analysisSession, type.lowerBound, printer)
            }
        }
        // Without this, it can render `kotlin.String & kotlin.Any`, which causes a syntax error.
        definitelyNotNullTypeRenderer = object : KaDefinitelyNotNullTypeRenderer {
            override fun renderType(
                analysisSession: KaSession,
                type: KaDefinitelyNotNullType,
                typeRenderer: KaTypeRenderer,
                printer: PrettyPrinter
            ) {
                typeRenderer.renderType(analysisSession, type.original, printer)
            }
        }
        // Listing variances will cause a syntax error.
        typeProjectionRenderer = KaTypeProjectionRenderer.WITHOUT_VARIANCE
        functionalTypeRenderer = KaFunctionalTypeRenderer.AS_FUNCTIONAL_TYPE
    }

    context (KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun JvmType.toKtType(useSitePosition: PsiElement): KaType? = when (this) {
        is PsiType -> if (isValid) {
            try {
                asKaType(useSitePosition)
            } catch (e: Throwable) {
                if (e is ControlFlowException) throw e

                // Some requests from Java side do not have a type. For example, in `var foo = dep.<caret>foo();`, we cannot guess
                // the type of `foo()`. In this case, the request passes "PsiType:null" whose name is "null" as a text. The analysis
                // API cannot get a KaType from this weird type. We return `Any?` for this case.
                builtinTypes.nullableAny
            }
        } else {
            null
        }

        else -> null
    }

    context (KaSession)
    fun ExpectedType.toKtTypeWithNullability(useSitePosition: PsiElement): KaType? {
        val nullability = if (this is ExpectedTypeWithNullability) this.nullability else null
        val ktTypeNullability = when (nullability) {
            Nullability.NOT_NULL -> KaTypeNullability.NON_NULLABLE
            Nullability.NULLABLE -> KaTypeNullability.NULLABLE
            Nullability.UNKNOWN -> KaTypeNullability.UNKNOWN
            null -> null
        }
        return theType.toKtType(useSitePosition)?.let { if (ktTypeNullability == null) it else it.withNullability(ktTypeNullability) }
    }

    fun KaTypeNullability.toNullability() : Nullability {
        return when (this) {
            KaTypeNullability.NON_NULLABLE -> Nullability.NOT_NULL
            KaTypeNullability.NULLABLE -> Nullability.NULLABLE
            KaTypeNullability.UNKNOWN -> Nullability.UNKNOWN
        }
    }

    // inspect `type` recursively and call `predicate` on all types inside, return true if all calls returned true
    context (KaSession)
    private fun accept(type: KaType?, visited: MutableSet<KaType>, predicate: (KaType) -> Boolean) : Boolean {
        if (type == null || !visited.add(type)) return true
        if (!predicate.invoke(type)) return false
        return when (type) {
            is KaClassType -> {
                acceptTypeQualifiers(type.qualifiers, visited, predicate)
                        && (type !is KaFunctionType || (accept(type.returnType, visited,predicate) && accept(type.receiverType, visited, predicate)))
            }
            is KaClassErrorType -> acceptTypeQualifiers(type.qualifiers, visited, predicate)
            is KaFlexibleType -> accept(type.lowerBound, visited, predicate) && accept(type.upperBound, visited, predicate)
            is KaCapturedType -> accept(type.projection.type, visited, predicate)
            is KaDefinitelyNotNullType -> accept(type.original, visited, predicate)
            is KaIntersectionType -> type.conjuncts.all { accept(it, visited, predicate) }
            else -> true
        }
    }

    context (KaSession)
    private fun acceptTypeQualifiers(qualifiers: List<KaClassTypeQualifier>, visited: MutableSet<KaType>, predicate: (KaType) -> Boolean) =
        qualifiers.flatMap { it.typeArguments }.map { it.type }.all { accept(it, visited, predicate) }

    /**
     * return [ktType] if it's accessible in the newly created method, or some other sensible type that is (e.g. super type), or null if can't figure out which type to use
     */
    context (KaSession)
    private fun makeAccessibleInCreationPlace(ktType: KaType, call: KtElement): KaType? {
        var type = ktType
        do {
            if (allTypesInsideAreAccessible(type, call)) return ktType
            type = type.expandedSymbol?.superTypes?.firstOrNull() ?: return null
        }
        while(true)
    }

    context (KaSession)
    private fun allTypesInsideAreAccessible(ktType: KaType, call: KtElement) : Boolean {
        fun KtTypeParameter.getOwningTypeParameterOwner(): KtTypeParameterListOwner? {
            val parameterList = parent as? KtTypeParameterList ?: return null
            return parameterList.parent as? KtTypeParameterListOwner
        }
        return accept(ktType, mutableSetOf()) { ktLeaf ->
                if (ktLeaf is KaTypeParameterType) {
                    // having `<T> caller(T t) { unknownMethod(t); }` the type `T` is not accessible in created method unknownMethod(t)
                    val owner = (ktLeaf.symbol.psi as? KtTypeParameter)?.getOwningTypeParameterOwner()
                    // todo must have been "insertion point" instead of `call`
                    if (owner == null) true
                    else if (owner is KtCallableDeclaration) false
                    else PsiTreeUtil.isAncestor(owner, call, false)
                } else {
                    // KaErrorType means this type is unresolved in the context of container
                    ktLeaf !is KaErrorType
                }
            }
    }

    fun JvmClass.toKtClassOrFile(): KtElement? = if (this is JvmClassWrapperForKtClass<*>) {
        ktClassOrFile
    } else {
        when (val psi = sourceElement) {
            is KtClassOrObject -> psi
            is KtLightClassForFacade -> psi.files.firstOrNull()
            is KtLightElement<*, *> -> psi.kotlinOrigin
            is KtFile -> psi
            else -> null
        }
    }

    context(KaSession)
    fun computeExpectedParams(call: KtCallElement, isAnnotation:Boolean=false): List<ExpectedParameter> {
        return call.valueArguments.mapIndexed { index, valueArgument ->
            valueArgument.getExpectedParameterInfo("p$index", isAnnotation && call.valueArguments.size == 1)
        }
    }
}
