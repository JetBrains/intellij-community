// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isImportDirectiveExpression
import org.jetbrains.kotlin.psi.psiUtil.isPackageDirectiveExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.calls.DslMarkerUtils
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.calls.smartcasts.SmartCastManager
import org.jetbrains.kotlin.resolve.descriptorUtil.classValueType
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindExclude
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.receivers.*
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.expressions.DoubleColonLHS
import org.jetbrains.kotlin.util.supertypesWithAny
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.util.*

@Suppress("ClassName")
sealed class CallType<TReceiver : KtElement?>(val descriptorKindFilter: DescriptorKindFilter) {
    object UNKNOWN : CallType<Nothing?>(DescriptorKindFilter.ALL)

    object DEFAULT : CallType<Nothing?>(DescriptorKindFilter.ALL)

    object DOT : CallType<KtExpression>(DescriptorKindFilter.ALL)

    object SAFE : CallType<KtExpression>(DescriptorKindFilter.ALL)

    object SUPER_MEMBERS : CallType<KtSuperExpression>(
        DescriptorKindFilter.CALLABLES exclude DescriptorKindExclude.Extensions exclude AbstractMembersExclude
    )

    object INFIX : CallType<KtExpression>(DescriptorKindFilter.FUNCTIONS exclude NonInfixExclude)

    object OPERATOR : CallType<KtExpression>(DescriptorKindFilter.FUNCTIONS exclude NonOperatorExclude)

    class CallableReference(settings: LanguageVersionSettings) :
        CallType<KtExpression?>(DescriptorKindFilter.CALLABLES exclude LocalsAndSyntheticExclude(settings)) {
        override fun equals(other: Any?): Boolean = other is CallableReference
        override fun hashCode(): Int = javaClass.hashCode()
    }

    object IMPORT_DIRECTIVE : CallType<KtExpression?>(DescriptorKindFilter.ALL)

    object PACKAGE_DIRECTIVE : CallType<KtExpression?>(DescriptorKindFilter.PACKAGES)

    object TYPE : CallType<KtExpression?>(
        DescriptorKindFilter(DescriptorKindFilter.CLASSIFIERS_MASK or DescriptorKindFilter.PACKAGES_MASK)
                exclude DescriptorKindExclude.EnumEntry
    )

    object DELEGATE : CallType<KtExpression?>(DescriptorKindFilter.FUNCTIONS exclude NonOperatorExclude)

    object ANNOTATION : CallType<KtExpression?>(
        DescriptorKindFilter(DescriptorKindFilter.CLASSIFIERS_MASK or DescriptorKindFilter.PACKAGES_MASK)
                exclude NonAnnotationClassifierExclude
    )

    private object NonInfixExclude : DescriptorKindExclude() {
        override fun excludes(descriptor: DeclarationDescriptor) =
            !(descriptor is SimpleFunctionDescriptor && descriptor.isInfix)

        override val fullyExcludedDescriptorKinds: Int
            get() = 0
    }

    private object NonOperatorExclude : DescriptorKindExclude() {
        override fun excludes(descriptor: DeclarationDescriptor) =
            !(descriptor is SimpleFunctionDescriptor && descriptor.isOperator)

        override val fullyExcludedDescriptorKinds: Int
            get() = 0
    }

    private class LocalsAndSyntheticExclude(private val settings: LanguageVersionSettings) : DescriptorKindExclude() {
        // Currently, Kotlin doesn't support references to local variables
        // References to Java synthetic properties are supported only since Kotlin 1.9
        override fun excludes(descriptor: DeclarationDescriptor): Boolean  =
            descriptor !is CallableMemberDescriptor || descriptor.kind == CallableMemberDescriptor.Kind.SYNTHESIZED &&
                    !settings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties)

        override val fullyExcludedDescriptorKinds: Int
            get() = 0
    }

    private object NonAnnotationClassifierExclude : DescriptorKindExclude() {
        override fun excludes(descriptor: DeclarationDescriptor): Boolean {
            if (descriptor !is ClassifierDescriptor) return false
            return descriptor !is ClassDescriptor || descriptor.kind != ClassKind.ANNOTATION_CLASS
        }

        override val fullyExcludedDescriptorKinds: Int get() = 0
    }

    private object AbstractMembersExclude : DescriptorKindExclude() {
        override fun excludes(descriptor: DeclarationDescriptor) =
            descriptor is CallableMemberDescriptor && descriptor.modality == Modality.ABSTRACT

        override val fullyExcludedDescriptorKinds: Int
            get() = 0
    }
}

@Suppress("ClassName")
sealed class CallTypeAndReceiver<TReceiver : KtElement?, out TCallType : CallType<TReceiver>>(
    val callType: TCallType,
    val receiver: TReceiver
) {
    object UNKNOWN : CallTypeAndReceiver<Nothing?, CallType.UNKNOWN>(CallType.UNKNOWN, null)
    object DEFAULT : CallTypeAndReceiver<Nothing?, CallType.DEFAULT>(CallType.DEFAULT, null)
    class DOT(receiver: KtExpression) : CallTypeAndReceiver<KtExpression, CallType.DOT>(CallType.DOT, receiver)
    class SAFE(receiver: KtExpression) : CallTypeAndReceiver<KtExpression, CallType.SAFE>(CallType.SAFE, receiver)
    class SUPER_MEMBERS(receiver: KtSuperExpression) : CallTypeAndReceiver<KtSuperExpression, CallType.SUPER_MEMBERS>(
        CallType.SUPER_MEMBERS, receiver
    )

    class INFIX(receiver: KtExpression) : CallTypeAndReceiver<KtExpression, CallType.INFIX>(CallType.INFIX, receiver)
    class OPERATOR(receiver: KtExpression) : CallTypeAndReceiver<KtExpression, CallType.OPERATOR>(CallType.OPERATOR, receiver)
    class CALLABLE_REFERENCE(
        receiver: KtExpression?,
        val settings: LanguageVersionSettings
    ) : CallTypeAndReceiver<KtExpression?, CallType.CallableReference>(CallType.CallableReference(settings), receiver)

    class IMPORT_DIRECTIVE(receiver: KtExpression?) : CallTypeAndReceiver<KtExpression?, CallType.IMPORT_DIRECTIVE>(
        CallType.IMPORT_DIRECTIVE, receiver
    )

    class PACKAGE_DIRECTIVE(receiver: KtExpression?) :
        CallTypeAndReceiver<KtExpression?, CallType.PACKAGE_DIRECTIVE>(CallType.PACKAGE_DIRECTIVE, receiver)

    class TYPE(receiver: KtExpression?) : CallTypeAndReceiver<KtExpression?, CallType.TYPE>(CallType.TYPE, receiver)
    class DELEGATE(receiver: KtExpression?) : CallTypeAndReceiver<KtExpression?, CallType.DELEGATE>(CallType.DELEGATE, receiver)
    class ANNOTATION(receiver: KtExpression?) : CallTypeAndReceiver<KtExpression?, CallType.ANNOTATION>(CallType.ANNOTATION, receiver)

    companion object {
        fun detect(expression: KtSimpleNameExpression): CallTypeAndReceiver<*, *> {
            val parent = expression.parent
            if (parent is KtCallableReferenceExpression && expression == parent.callableReference) {
                return CALLABLE_REFERENCE(parent.receiverExpression, expression.languageVersionSettings)
            }

            val receiverExpression = expression.getReceiverExpression()

            if (expression.isImportDirectiveExpression()) {
                return IMPORT_DIRECTIVE(receiverExpression)
            }

            if (expression.isPackageDirectiveExpression()) {
                return PACKAGE_DIRECTIVE(receiverExpression)
            }

            if (parent is KtUserType) {
                val constructorCallee = (parent.parent as? KtTypeReference)?.parent as? KtConstructorCalleeExpression
                if (constructorCallee != null && constructorCallee.parent is KtAnnotationEntry) {
                    return ANNOTATION(receiverExpression)
                }

                return TYPE(receiverExpression)
            }

            when (expression) {
                is KtOperationReferenceExpression -> {
                    if (receiverExpression == null) {
                        return UNKNOWN // incomplete code
                    }
                    return when (parent) {
                        is KtBinaryExpression -> {
                            if (parent.operationToken == KtTokens.IDENTIFIER)
                                INFIX(receiverExpression)
                            else
                                OPERATOR(receiverExpression)
                        }

                        is KtUnaryExpression -> OPERATOR(receiverExpression)

                        else -> error("Unknown parent for KtOperationReferenceExpression: $parent with text '${parent.text}'")
                    }
                }

                is KtNameReferenceExpression -> {
                    if (receiverExpression == null) {
                        return DEFAULT
                    }

                    if (receiverExpression is KtSuperExpression) {
                        return SUPER_MEMBERS(receiverExpression)
                    }

                    return when (parent) {
                        is KtCallExpression -> {
                            if ((parent.parent as KtQualifiedExpression).operationSign == KtTokens.SAFE_ACCESS)
                                SAFE(receiverExpression)
                            else
                                DOT(receiverExpression)
                        }

                        is KtQualifiedExpression -> {
                            if (parent.operationSign == KtTokens.SAFE_ACCESS)
                                SAFE(receiverExpression)
                            else
                                DOT(receiverExpression)
                        }

                        else -> error("Unknown parent for KtNameReferenceExpression with receiver: $parent")
                    }
                }

                else -> return UNKNOWN
            }
        }
    }
}

data class ReceiverType(
    val type: KotlinType,
    val receiverIndex: Int,
    val implicitValue: ReceiverValue? = null
) {
    val implicit: Boolean get() = implicitValue != null

    fun extractDslMarkers() =
        implicitValue?.let(DslMarkerUtils::extractDslMarkerFqNames)?.all()
            ?: DslMarkerUtils.extractDslMarkerFqNames(type)
}

fun CallTypeAndReceiver<*, *>.receiverTypes(
    bindingContext: BindingContext,
    contextElement: PsiElement,
    moduleDescriptor: ModuleDescriptor,
    resolutionFacade: ResolutionFacade,
    stableSmartCastsOnly: Boolean
): List<KotlinType>? {
    return receiverTypesWithIndex(bindingContext, contextElement, moduleDescriptor, resolutionFacade, stableSmartCastsOnly)?.map { it.type }
}

fun CallTypeAndReceiver<*, *>.receiverTypesWithIndex(
    bindingContext: BindingContext,
    contextElement: PsiElement,
    moduleDescriptor: ModuleDescriptor,
    resolutionFacade: ResolutionFacade,
    stableSmartCastsOnly: Boolean,
    withImplicitReceiversWhenExplicitPresent: Boolean = false
): List<ReceiverType>? {
    val languageVersionSettings = resolutionFacade.languageVersionSettings

    val receiverExpression: KtExpression?
    when (this) {
        is CallTypeAndReceiver.CALLABLE_REFERENCE -> {
            if (receiver != null) {
                return when (val lhs = bindingContext[BindingContext.DOUBLE_COLON_LHS, receiver] ?: return emptyList()) {
                    is DoubleColonLHS.Type -> listOf(ReceiverType(lhs.type, 0))

                    is DoubleColonLHS.Expression -> {
                        val receiverValue = ExpressionReceiver.create(receiver, lhs.type, bindingContext)
                        receiverValueTypes(
                            receiverValue, lhs.dataFlowInfo, bindingContext,
                            moduleDescriptor, stableSmartCastsOnly,
                            resolutionFacade
                        ).map { ReceiverType(it, 0) }
                    }
                }
            } else {
                return emptyList()
            }
        }

        is CallTypeAndReceiver.DEFAULT -> receiverExpression = null

        is CallTypeAndReceiver.DOT -> receiverExpression = receiver
        is CallTypeAndReceiver.SAFE -> receiverExpression = receiver
        is CallTypeAndReceiver.INFIX -> receiverExpression = receiver
        is CallTypeAndReceiver.OPERATOR -> receiverExpression = receiver
        is CallTypeAndReceiver.DELEGATE -> receiverExpression = receiver

        is CallTypeAndReceiver.SUPER_MEMBERS -> {
            val qualifier = receiver.superTypeQualifier
            return if (qualifier != null) {
                listOfNotNull(bindingContext.getType(receiver)).map { ReceiverType(it, 0) }
            } else {
                val resolutionScope = contextElement.getResolutionScope(bindingContext, resolutionFacade)
                val classDescriptor =
                    resolutionScope.ownerDescriptor.parentsWithSelf.firstIsInstanceOrNull<ClassDescriptor>() ?: return emptyList()
                classDescriptor.typeConstructor.supertypesWithAny().map { ReceiverType(it, 0) }
            }
        }

        is CallTypeAndReceiver.IMPORT_DIRECTIVE,
        is CallTypeAndReceiver.PACKAGE_DIRECTIVE,
        is CallTypeAndReceiver.TYPE,
        is CallTypeAndReceiver.ANNOTATION,
        is CallTypeAndReceiver.UNKNOWN ->
            return null
    }

    val resolutionScope = contextElement.getResolutionScope(bindingContext, resolutionFacade)

    fun extractReceiverTypeFrom(descriptor: ClassDescriptor): KotlinType? = descriptor.classValueType

    fun tryExtractReceiver(context: BindingContext) = context.get(BindingContext.QUALIFIER, receiverExpression)

    fun tryExtractClassDescriptor(context: BindingContext): ClassDescriptor? =
            (tryExtractReceiver(context) as? ClassQualifier)?.descriptor

    fun tryExtractClassDescriptorFromAlias(context: BindingContext): ClassDescriptor? =
            (tryExtractReceiver(context) as? TypeAliasQualifier)?.classDescriptor

    fun extractReceiverTypeFrom(context: BindingContext, receiverExpression: KtExpression): KotlinType? {
        return context.getType(receiverExpression) ?: tryExtractClassDescriptor(context)?.let { extractReceiverTypeFrom(it) }
               ?: tryExtractClassDescriptorFromAlias(context)?.let { extractReceiverTypeFrom(it) }
    }

    val expressionReceiver = receiverExpression?.let {
        val receiverType = extractReceiverTypeFrom(bindingContext, receiverExpression) ?: return emptyList()
        ExpressionReceiver.create(receiverExpression, receiverType, bindingContext)
    }

    val implicitReceiverValues = resolutionScope.getImplicitReceiversWithInstance(
        excludeShadowedByDslMarkers = languageVersionSettings.supportsFeature(LanguageFeature.DslMarkersSupport)
    ).map { it.value }

    val dataFlowInfo = bindingContext.getDataFlowInfoBefore(contextElement)

    val result = ArrayList<ReceiverType>()

    var receiverIndex = 0

    fun addReceiverType(receiverValue: ReceiverValue, implicit: Boolean) {
        val types = receiverValueTypes(
            receiverValue, dataFlowInfo, bindingContext, moduleDescriptor, stableSmartCastsOnly,
            resolutionFacade
        )

        types.mapTo(result) { type -> ReceiverType(type, receiverIndex, receiverValue.takeIf { implicit }) }

        receiverIndex++
    }
    if (withImplicitReceiversWhenExplicitPresent || expressionReceiver == null) {
        implicitReceiverValues.forEach { addReceiverType(it, true) }
    }
    if (expressionReceiver != null) {
        addReceiverType(expressionReceiver, false)
    }
    return result
}

@OptIn(FrontendInternals::class)
private fun receiverValueTypes(
    receiverValue: ReceiverValue,
    dataFlowInfo: DataFlowInfo,
    bindingContext: BindingContext,
    moduleDescriptor: ModuleDescriptor,
    stableSmartCastsOnly: Boolean,
    resolutionFacade: ResolutionFacade
): List<KotlinType> {
    val languageVersionSettings = resolutionFacade.languageVersionSettings
    val dataFlowValueFactory = resolutionFacade.dataFlowValueFactory
    val smartCastManager = resolutionFacade.frontendService<SmartCastManager>()
    val dataFlowValue = dataFlowValueFactory.createDataFlowValue(receiverValue, bindingContext, moduleDescriptor)
    return if (dataFlowValue.isStable || !stableSmartCastsOnly) { // we don't include smart cast receiver types for "unstable" receiver value to mark members grayed
        smartCastManager.getSmartCastVariantsWithLessSpecificExcluded(
            receiverValue,
            bindingContext,
            moduleDescriptor,
            dataFlowInfo,
            languageVersionSettings,
            dataFlowValueFactory
        )
    } else {
        listOf(receiverValue.type)
    }
}

fun SmartCastManager.getSmartCastVariantsWithLessSpecificExcluded(
    receiverToCast: ReceiverValue,
    bindingContext: BindingContext,
    containingDeclarationOrModule: DeclarationDescriptor,
    dataFlowInfo: DataFlowInfo,
    languageVersionSettings: LanguageVersionSettings,
    dataFlowValueFactory: DataFlowValueFactory
): List<KotlinType> {
    val variants = getSmartCastVariants(
        receiverToCast,
        bindingContext,
        containingDeclarationOrModule,
        dataFlowInfo,
        languageVersionSettings,
        dataFlowValueFactory
    )
    return variants.filter { type ->
        variants.all { another -> another === type || chooseMoreSpecific(type, another).let { it == null || it === type } }
    }
}

private fun chooseMoreSpecific(type1: KotlinType, type2: KotlinType): KotlinType? {
    val type1IsSubtype = KotlinTypeChecker.DEFAULT.isSubtypeOf(type1, type2)
    val type2IsSubtype = KotlinTypeChecker.DEFAULT.isSubtypeOf(type2, type1)

    if (type1IsSubtype && type2IsSubtype) {
        val flexible1 = type1.unwrap() as? FlexibleType
        val flexible2 = type2.unwrap() as? FlexibleType
        return when {
            flexible1 != null && flexible2 == null -> type2
            flexible2 != null && flexible1 == null -> type1
            else -> null //TODO?
        }
    }

    return type1.takeIf { type1IsSubtype }
        ?: type2.takeIf { type2IsSubtype }
}
