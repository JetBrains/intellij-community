// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder

import com.intellij.psi.PsiElement
import com.intellij.util.ArrayUtil
import org.jetbrains.kotlin.cfg.containingDeclarationForPseudocode
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CallableInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CallableKind
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.ParameterInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.TypeInfoBase
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.ClassInfo
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.getResolvableApproximations
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import java.util.*

/**
 * Represents a concrete type or a set of types yet to be inferred from an expression.
 */
abstract class TypeInfo(variance: Variance): TypeInfoBase(variance) {
    object Empty : TypeInfo(Variance.INVARIANT) {
        override fun getPossibleTypes(builder: CallableBuilder): List<KotlinType> = Collections.emptyList()
    }

    class ByExpression(val expression: KtExpression, variance: Variance) : TypeInfo(variance) {
        override fun getPossibleNamesFromExpression(bindingContext: BindingContext): Array<String> {
            return Fe10KotlinNameSuggester.suggestNamesByExpressionOnly(expression, bindingContext, { true }).toTypedArray()
        }

        override fun getPossibleTypes(builder: CallableBuilder): List<KotlinType> = expression.guessTypes(
            context = builder.currentFileContext,
            module = builder.currentFileModule,
            pseudocode = try {
                builder.pseudocode
            } catch (stackException: EmptyStackException) {
                val originalElement = builder.config.originalElement
                val containingDeclarationForPseudocode = originalElement.containingDeclarationForPseudocode

                // copy-pasted from org.jetbrains.kotlin.cfg.pseudocode.PseudocodeUtilsKt.getContainingPseudocode
                val enclosingPseudocodeDeclaration = (containingDeclarationForPseudocode as? KtFunctionLiteral)?.let { functionLiteral ->
                    functionLiteral.parents.firstOrNull { it is KtDeclaration && it !is KtFunctionLiteral } as? KtDeclaration
                } ?: containingDeclarationForPseudocode

                throw KotlinExceptionWithAttachments(stackException.message, stackException)
                    .withPsiAttachment("original_expression.txt", originalElement)
                    .withPsiAttachment("containing_declaration.txt", containingDeclarationForPseudocode)
                    .withPsiAttachment("enclosing_declaration.txt", enclosingPseudocodeDeclaration)
            }
        ).flatMap { it.getPossibleSupertypes(variance, builder) }
    }

    class ByTypeReference(val typeReference: KtTypeReference, variance: Variance) : TypeInfo(variance) {
        override fun getPossibleTypes(builder: CallableBuilder): List<KotlinType> =
            builder.currentFileContext[BindingContext.TYPE, typeReference].getPossibleSupertypes(variance, builder)
    }

    class ByType(val theType: KotlinType, variance: Variance) : TypeInfo(variance) {
        override fun getPossibleTypes(builder: CallableBuilder): List<KotlinType> =
            theType.getPossibleSupertypes(variance, builder)
    }

    class ByReceiverType(variance: Variance) : TypeInfo(variance) {
        override fun getPossibleTypes(builder: CallableBuilder): List<KotlinType> =
            (builder.placement as CallablePlacement.WithReceiver).receiverTypeCandidate.theType.getPossibleSupertypes(variance, builder)
    }

    class ByExplicitCandidateTypes(val types: List<KotlinType>) : TypeInfo(Variance.INVARIANT) {
        override fun getPossibleTypes(builder: CallableBuilder) = types
    }

    abstract class DelegatingTypeInfo(val delegate: TypeInfo) : TypeInfo(delegate.variance) {
        override val substitutionsAllowed: Boolean = delegate.substitutionsAllowed
        override fun getPossibleNamesFromExpression(bindingContext: BindingContext) =
            delegate.getPossibleNamesFromExpression(bindingContext)

        override fun getPossibleTypes(builder: CallableBuilder): List<KotlinType> = delegate.getPossibleTypes(builder)
    }

    class NoSubstitutions(delegate: TypeInfo) : DelegatingTypeInfo(delegate) {
        override val substitutionsAllowed: Boolean = false
    }

    class StaticContextRequired(delegate: TypeInfo) : DelegatingTypeInfo(delegate) {
        override val staticContextRequired: Boolean = true
    }

    class OfThis(delegate: TypeInfo) : DelegatingTypeInfo(delegate)

    override fun isOfThis(): Boolean = when (this) {
        is OfThis -> true
        is DelegatingTypeInfo -> delegate.isOfThis()
        else -> false
    }

    override fun isEmpty(): Boolean = this is Empty

    open fun getPossibleNamesFromExpression(bindingContext: BindingContext): Array<String> = ArrayUtil.EMPTY_STRING_ARRAY
    abstract fun getPossibleTypes(builder: CallableBuilder): List<KotlinType>

    private fun getScopeForTypeApproximation(config: CallableBuilderConfiguration, placement: CallablePlacement?): LexicalScope? {
        if (placement == null) return config.originalElement.getResolutionScope()

        val containingElement = when (placement) {
            is CallablePlacement.NoReceiver -> {
                placement.containingElement
            }
            is CallablePlacement.WithReceiver -> {
                val receiverClassDescriptor =
                    placement.receiverTypeCandidate.theType.constructor.declarationDescriptor
                val classDeclaration = receiverClassDescriptor?.let { DescriptorToSourceUtils.getSourceFromDescriptor(it) }
                if (!config.isExtension && classDeclaration != null) classDeclaration else config.currentFile
            }
        }
        return when (containingElement) {
            is KtClassOrObject -> (containingElement.resolveToDescriptorIfAny() as? ClassDescriptorWithResolutionScopes)?.scopeForMemberDeclarationResolution
            is KtBlockExpression -> (containingElement.statements.firstOrNull() ?: containingElement).getResolutionScope()
            is KtElement -> containingElement.containingKtFile.getResolutionScope()
            else -> null
        }
    }

    protected fun KotlinType?.getPossibleSupertypes(variance: Variance, callableBuilder: CallableBuilder): List<KotlinType> {
        if (this == null || ErrorUtils.containsErrorType(this)) {
            return Collections.singletonList(callableBuilder.currentFileModule.builtIns.anyType)
        }
        val scope = getScopeForTypeApproximation(callableBuilder.config, callableBuilder.placement)
        val approximations = getResolvableApproximations(scope, checkTypeParameters = false, allowIntersections = true)
        return when (variance) {
            Variance.IN_VARIANCE -> approximations.toList()
            else -> listOf(approximations.firstOrNull() ?: this)
        }
    }
}

fun TypeInfo(expressionOfType: KtExpression, variance: Variance): TypeInfo = TypeInfo.ByExpression(expressionOfType, variance)
fun TypeInfo(typeReference: KtTypeReference, variance: Variance): TypeInfo = TypeInfo.ByTypeReference(typeReference, variance)
fun TypeInfo(theType: KotlinType, variance: Variance): TypeInfo = TypeInfo.ByType(theType, variance)

fun TypeInfo.noSubstitutions(): TypeInfo = (this as? TypeInfo.NoSubstitutions) ?: TypeInfo.NoSubstitutions(this)

fun TypeInfo.forceNotNull(): TypeInfo {
    class ForcedNotNull(delegate: TypeInfo) : TypeInfo.DelegatingTypeInfo(delegate) {
        override fun getPossibleTypes(builder: CallableBuilder): List<KotlinType> =
            super.getPossibleTypes(builder).map { it.makeNotNullable() }
    }

    return (this as? ForcedNotNull) ?: ForcedNotNull(this)
}

fun TypeInfo.ofThis() = TypeInfo.OfThis(this)

class ClassWithPrimaryConstructorInfo(
    val classInfo: ClassInfo,
    expectedTypeInfo: TypeInfo,
    modifierList: KtModifierList? = null,
    val primaryConstructorVisibility: DescriptorVisibility? = null
) : CallableInfo(
    classInfo.name,
    TypeInfo.Empty,
    expectedTypeInfo.forceNotNull(),
    Collections.emptyList(),
    classInfo.typeArguments,
    false,
    modifierList = modifierList
) {
    override val kind: CallableKind get() = CallableKind.CLASS_WITH_PRIMARY_CONSTRUCTOR
    override val parameterInfos: List<ParameterInfo> get() = classInfo.parameterInfos

    override fun copy(
        receiverTypeInfo: TypeInfoBase,
        possibleContainers: List<KtElement>,
        modifierList: KtModifierList?
    ) = throw UnsupportedOperationException()
}

class ConstructorInfo(
    override val parameterInfos: List<ParameterInfo>,
    val targetClass: PsiElement,
    val isPrimary: Boolean = false,
    modifierList: KtModifierList? = null,
    val withBody: Boolean = false,
    val annotations: List<KtAnnotationEntry> = emptyList()
) : CallableInfo("", TypeInfo.Empty, TypeInfo.Empty, Collections.emptyList(), Collections.emptyList(), false, modifierList = modifierList) {
    override val kind: CallableKind get() = CallableKind.CONSTRUCTOR

    override fun copy(
        receiverTypeInfo: TypeInfoBase,
        possibleContainers: List<KtElement>,
        modifierList: KtModifierList?
    ) = throw UnsupportedOperationException()
}
