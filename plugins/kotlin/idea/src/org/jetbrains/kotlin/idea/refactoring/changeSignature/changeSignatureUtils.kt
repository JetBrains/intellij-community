// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.CallerUsageInfo
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.OverriderUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.DeferredJavaMethodKotlinCallerUsage
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.JavaMethodKotlinUsageWithDelegate
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.KotlinCallableDefinitionUsage
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.KotlinCallerUsage
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.KotlinTypeSubstitution
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.scopes.utils.findVariable
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

fun KtNamedDeclaration.getDeclarationBody(): KtElement? = when (this) {
    is KtClassOrObject -> getSuperTypeList()
    is KtPrimaryConstructor -> getContainingClassOrObject().getSuperTypeList()
    is KtSecondaryConstructor -> getDelegationCall()
    is KtNamedFunction -> bodyExpression
    else -> null
}

fun PsiElement.isCaller(allUsages: Array<out UsageInfo>): Boolean {
    val primaryConstructor = (this as? KtClass)?.primaryConstructor
    val elementsToSearch = if (primaryConstructor != null) listOf(primaryConstructor, this) else listOf(this)
    return allUsages.asSequence()
        .filter {
            val usage = (it as? JavaMethodKotlinUsageWithDelegate<*>)?.delegateUsage ?: it
            usage is KotlinCallerUsage || usage is DeferredJavaMethodKotlinCallerUsage || usage is CallerUsageInfo || (usage is OverriderUsageInfo && !usage.isOriginalOverrider)
        }
        .any { it.element in elementsToSearch }
}

fun KtElement.isInsideOfCallerBody(allUsages: Array<out UsageInfo>): Boolean {
    val container = parentsWithSelf.firstOrNull {
        it is KtNamedFunction || it is KtConstructor<*> || it is KtClassOrObject
    } as? KtNamedDeclaration ?: return false
    val body = container.getDeclarationBody() ?: return false
    return body.textRange.contains(textRange) && container.isCaller(allUsages)
}

fun getCallableSubstitutor(
    baseFunction: KotlinCallableDefinitionUsage<*>,
    derivedCallable: KotlinCallableDefinitionUsage<*>
): TypeSubstitutor? {
    val currentBaseFunction = baseFunction.currentCallableDescriptor ?: return null
    val currentDerivedFunction = derivedCallable.currentCallableDescriptor ?: return null
    val substitution = getCallableSubstitution(currentBaseFunction, currentDerivedFunction) ?: return null
    return TypeSubstitutor.create(substitution)
}

private fun getCallableSubstitution(baseCallable: CallableDescriptor, derivedCallable: CallableDescriptor): KotlinTypeSubstitution? {
    val baseClass = baseCallable.containingDeclaration as? ClassDescriptor ?: return null
    val derivedClass = derivedCallable.containingDeclaration as? ClassDescriptor ?: return null
    val substitution = getTypeSubstitution(baseClass.defaultType, derivedClass.defaultType) ?: return null

    for ((baseParam, derivedParam) in baseCallable.typeParameters.zip(derivedCallable.typeParameters)) {
        substitution[baseParam.typeConstructor] = TypeProjectionImpl(derivedParam.defaultType)
    }

    return substitution
}

fun KotlinType.renderTypeWithSubstitution(substitutor: TypeSubstitutor?, defaultText: String, inArgumentPosition: Boolean): String {
    val newType = substitutor?.substitute(this, Variance.INVARIANT) ?: return defaultText
    val renderer = if (inArgumentPosition)
        IdeDescriptorRenderers.SOURCE_CODE_NOT_NULL_TYPE_APPROXIMATION
    else
        IdeDescriptorRenderers.SOURCE_CODE

    return renderer.renderType(newType)
}

// This method is used to create full copies of functions (including copies of all types)
// It's needed to prevent accesses to PSI (e.g. using LazyJavaClassifierType properties) when Change signature invalidates it
// See KotlinChangeSignatureTest.testSAMChangeMethodReturnType
fun DeclarationDescriptor.createDeepCopy(): DeclarationDescriptor =
    (this as? JavaMethodDescriptor)?.substitute(TypeSubstitutor.create(ForceTypeCopySubstitution)) ?: this

private object ForceTypeCopySubstitution : TypeSubstitution() {
    override fun get(key: KotlinType) = with(key) {
        if (isError) return@with asTypeProjection()
        val type = if (this is FlexibleType)
            KotlinTypeFactory.flexibleType(lowerBound.copyAsSimpleType(), upperBound.copyAsSimpleType())
        else
            copyAsSimpleType()

        type.asTypeProjection()
    }

    override fun isEmpty() = false
}

private fun KotlinType.copyAsSimpleType(): SimpleType = KotlinTypeFactory.simpleTypeWithNonTrivialMemberScope(
    annotations.toDefaultAttributes(),
    constructor,
    arguments,
    isMarkedNullable,
    memberScope,
)

fun suggestReceiverNames(project: Project, descriptor: CallableDescriptor): List<String> {
    val callable = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor) as? KtCallableDeclaration ?: return emptyList()
    val bodyScope = (callable as? KtFunction)?.bodyExpression?.let { it.getResolutionScope(it.analyze(), it.getResolutionFacade()) }
    val paramNames = descriptor.valueParameters.map { it.name.asString() }
    val validator = bodyScope?.let { scope ->
        CollectingNameValidator(paramNames) {
            scope.findVariable(Name.identifier(it), NoLookupLocation.FROM_IDE) == null
        }
    } ?: CollectingNameValidator(paramNames)
    val receiverType = descriptor.extensionReceiverParameter?.type ?: return emptyList()
    return Fe10KotlinNameSuggester.suggestNamesByType(receiverType, validator, "receiver")
}

internal val ChangeInfo.asKotlinChangeInfo: KotlinChangeInfo?
    get() = when (this) {
        is KotlinChangeInfo -> this
        is KotlinChangeInfoWrapper -> delegate
        else -> null
    }

fun KotlinTypeInfo.getReceiverTypeText(): String {
    val text = render()
    return when {
        text.startsWith("(") && text.endsWith(")") -> text
        type is DefinitelyNotNullType || type?.isFunctionType == true -> "($text)"
        else -> text
    }
}
