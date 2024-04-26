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
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtDefinitelyNotNullTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtFlexibleTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtTypeProjectionRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.api.types.KtIntersectionType
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
object K2CreateFromUsageUtil {
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
    context (KtAnalysisSession)
    fun getExpectedTypeByStringTemplateEntry(expression: KtExpression): KtType? {
        var e:PsiElement = expression
        while (e is KtExpression && e !is KtStringTemplateEntry) {
            val parent = e.parent
            if (parent is KtQualifiedExpression && parent.selectorExpression != e) break
            e = parent
        }
        if (e is KtStringTemplateEntry) {
            return withValidityAssertion { analysisSession.builtinTypes.STRING }
        }
        return null
    }

    // Given: `fun f(): T = expression`
    // Expected type of `expression` is `T`
    context (KtAnalysisSession)
    private fun getExpectedTypeByFunctionExpressionBody(expression: KtExpression): KtType? {
        var e:PsiElement = expression
        while (e is KtExpression && e !is KtFunction) {
            e=e.parent
        }
        if (e is KtFunction && e.bodyBlockExpression == null && e.bodyExpression?.isAncestor(expression) == true) {
            return e.getExpectedType() ?: withValidityAssertion { analysisSession.builtinTypes.ANY }
        }
        return null
    }

    context (KtAnalysisSession)
    internal fun KtType.convertToJvmType(useSitePosition: PsiElement): JvmType? = asPsiType(useSitePosition, allowErrorTypes = false)

    context (KtAnalysisSession)
    internal fun KtExpression.getClassOfExpressionType(): PsiElement? = when (val symbol = resolveExpression()) {
        //is KtCallableSymbol -> symbol.returnType.expandedClassSymbol // When the receiver is a function call or access to a variable
        is KtClassLikeSymbol -> symbol // When the receiver is an object
        else -> getKtType()?.expandedClassSymbol
    }?.psi

    context (KtAnalysisSession)
    internal fun ValueArgument.getExpectedParameterInfo(defaultParameterName: ()->String): ExpectedParameter {
        val parameterNameAsString = getArgumentName()?.asName?.asString()
        val argumentExpression = getArgumentExpression()
        val expectedArgumentType = argumentExpression?.getKtType()
        val parameterNames = parameterNameAsString?.let { sequenceOf(it) } ?: expectedArgumentType?.let { NAME_SUGGESTER.suggestTypeNames(it) }
        val jvmParameterType = expectedArgumentType?.convertToJvmType(argumentExpression)
        val expectedType = if (jvmParameterType == null) ExpectedTypeWithNullability.INVALID_TYPE else ExpectedKotlinType(expectedArgumentType, jvmParameterType)
        val names = parameterNames?.toList()?.toTypedArray() ?: arrayOf(defaultParameterName.invoke())
        return expectedParameter(expectedType, *names)
    }

    context (KtAnalysisSession)
    internal fun KtSimpleNameExpression.getReceiverOrContainerClass(containerPsi: PsiElement?): JvmClass? {
        return when(containerPsi) {
            is PsiClass -> containerPsi
            is KtClass -> containerPsi.getContainerClass()
            is KtClassOrObject -> containerPsi.toLightClass()
            else -> getContainerClass()
        }
    }
    context (KtAnalysisSession)
    internal fun KtSimpleNameExpression.getReceiverOrContainerPsiElement(): PsiElement? {
        val receiverExpression = getReceiverExpression()
        return when (val ktClassOrPsiClass = receiverExpression?.getClassOfExpressionType()) {
            is PsiClass -> ktClassOrPsiClass
            is KtClassOrObject -> ktClassOrPsiClass
            else -> K2CreateFunctionFromUsageBuilder.computeImplicitReceiverClass(this) ?: getNonStrictParentOfType<KtClassOrObject>()
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
            override fun renderType(
                analysisSession: KtAnalysisSession,
                type: KtFlexibleType,
                typeRenderer: KtTypeRenderer,
                printer: PrettyPrinter
            ) {
                typeRenderer.renderType(analysisSession, type.lowerBound, printer)
            }
        }
        // Without this, it can render `kotlin.String & kotlin.Any`, which causes a syntax error.
        definitelyNotNullTypeRenderer = object : KtDefinitelyNotNullTypeRenderer {
            override fun renderType(
                analysisSession: KtAnalysisSession,
                type: KtDefinitelyNotNullType,
                typeRenderer: KtTypeRenderer,
                printer: PrettyPrinter
            ) {
                typeRenderer.renderType(analysisSession, type.original, printer)
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

    context (KtAnalysisSession)
    fun ExpectedType.toKtTypeWithNullability(useSitePosition: PsiElement): KtType? {
        val nullability = if (this is ExpectedTypeWithNullability) this.nullability else null
        val ktTypeNullability = when (nullability) {
            Nullability.NOT_NULL -> KtTypeNullability.NON_NULLABLE
            Nullability.NULLABLE -> KtTypeNullability.NULLABLE
            Nullability.UNKNOWN -> KtTypeNullability.UNKNOWN
            null -> null
        }
        return theType.toKtType(useSitePosition)?.let { if (ktTypeNullability == null) it else it.withNullability(ktTypeNullability) }
    }

    fun KtTypeNullability.toNullability() : Nullability {
        return when (this) {
            KtTypeNullability.NON_NULLABLE -> Nullability.NOT_NULL
            KtTypeNullability.NULLABLE -> Nullability.NULLABLE
            KtTypeNullability.UNKNOWN -> Nullability.UNKNOWN
        }
    }

    // inspect `type` recursively and call `predicate` on all types inside, return true if all calls returned true
    context (KtAnalysisSession)
    private fun accept(type: KtType?, visited: MutableSet<KtType>, predicate: (KtType) -> Boolean) : Boolean {
        if (type == null || !visited.add(type)) return true
        if (!predicate.invoke(type)) return false
        return when (type) {
            is KtClassType -> type.qualifiers.flatMap { it.typeArguments }.map { it.type}.all { accept(it, visited, predicate)}
                    && (type !is KtFunctionalType || (accept(type.returnType, visited,predicate) && accept(type.receiverType, visited, predicate)))
            is KtFlexibleType -> accept(type.lowerBound, visited, predicate) && accept(type.upperBound, visited, predicate)
            is KtCapturedType -> accept(type.projection.type, visited, predicate)
            is KtDefinitelyNotNullType -> accept(type.original, visited, predicate)
            is KtIntersectionType -> type.conjuncts.all { accept(it, visited, predicate) }
            else -> true
        }
    }

    /**
     * return [type] if it's accessible in the newly created method, or some other sensible type that is (e.g. super type), or null if can't figure out which type to use
     */
    context (KtAnalysisSession)
    fun makeAccessibleInCreationPlace(ktType: KtType, call: KtElement): KtType? {
        var type = ktType
        do {
            if (allTypesInsideAreAccessible(type, call)) return ktType
            type = type.expandedClassSymbol?.superTypes?.firstOrNull() ?: return null
        }
        while(true);
    }

    context (KtAnalysisSession)
    fun allTypesInsideAreAccessible(ktType: KtType, call: KtElement) : Boolean {
        fun KtTypeParameter.getOwningTypeParameterOwner(): KtTypeParameterListOwner? {
            val parameterList = parent as? KtTypeParameterList ?: return null
            return parameterList.parent as? KtTypeParameterListOwner
        }
        return accept(ktType, mutableSetOf()) { ktLeaf ->
                if (ktLeaf is KtTypeParameterType) {
                    // having `<T> caller(T t) { unknownMethod(t); }` the type `T` is not accessible in created method unknownMethod(t)
                    val owner = (ktLeaf.symbol.psi as? KtTypeParameter)?.getOwningTypeParameterOwner()
                    // todo must have been "insertion point" instead of `call`
                    if (owner == null) true
                    else if (owner is KtCallableDeclaration) false
                    else PsiTreeUtil.isAncestor(owner, call, false)
                } else {
                    // KtErrorType means this type is unresolved in the context of container
                    ktLeaf !is KtErrorType
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
}
