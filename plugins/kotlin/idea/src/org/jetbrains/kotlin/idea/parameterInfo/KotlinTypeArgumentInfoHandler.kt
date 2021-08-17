// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.resolveCandidates
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.calls.callUtil.getCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny
import org.jetbrains.kotlin.types.typeUtil.nullability

class KotlinClassConstructorInfoHandler : KotlinTypeArgumentInfoHandlerBase<ClassConstructorDescriptor>() {
    override fun fetchTypeParameters(descriptor: ClassConstructorDescriptor): List<TypeParameterDescriptor> = descriptor.typeParameters

    override fun findParameterOwners(argumentList: KtTypeArgumentList): Collection<ClassConstructorDescriptor>? {
        val userType = argumentList.parent as? KtUserType ?: return null
        val descriptors =
            userType.referenceExpression?.resolveMainReferenceToDescriptors()?.mapNotNull { it as? ClassConstructorDescriptor }
        return descriptors?.takeIf { it.isNotEmpty() }
    }

    override fun getArgumentListAllowedParentClasses() = setOf(KtUserType::class.java)
}

class KotlinClassTypeArgumentInfoHandler : KotlinTypeArgumentInfoHandlerBase<ClassDescriptor>() {
    override fun fetchTypeParameters(descriptor: ClassDescriptor) = descriptor.typeConstructor.parameters

    override fun findParameterOwners(argumentList: KtTypeArgumentList): Collection<ClassDescriptor>? {
        val userType = argumentList.parent as? KtUserType ?: return null
        val descriptors = userType.referenceExpression?.resolveMainReferenceToDescriptors()?.mapNotNull { it as? ClassDescriptor }
        return descriptors?.takeIf { it.isNotEmpty() }
    }

    override fun getArgumentListAllowedParentClasses() = setOf(KtUserType::class.java)
}

class KotlinFunctionTypeArgumentInfoHandler : KotlinTypeArgumentInfoHandlerBase<FunctionDescriptor>() {
    override fun fetchTypeParameters(descriptor: FunctionDescriptor) = descriptor.typeParameters

    override fun findParameterOwners(argumentList: KtTypeArgumentList): Collection<FunctionDescriptor>? {
        val callElement = argumentList.parent as? KtCallElement ?: return null
        val bindingContext = argumentList.analyze(BodyResolveMode.PARTIAL)
        val call = callElement.getCall(bindingContext) ?: return null
        val candidates = call.resolveCandidates(bindingContext, callElement.getResolutionFacade())
        return candidates
            .map { it.resultingDescriptor }
            .distinctBy { buildPresentation(it.typeParameters, -1).first }
    }

    override fun getArgumentListAllowedParentClasses() = setOf(KtCallElement::class.java)
}

abstract class KotlinTypeArgumentInfoHandlerBase<TParameterOwner : Any> :
    AbstractKotlinTypeArgumentInfoHandler<TParameterOwner, TypeParameterDescriptor, KotlinType>() {

    override fun TypeParameterDescriptor.isReified(): Boolean = isReified

    override fun TypeParameterDescriptor.variance(): String = when (variance) {
        Variance.INVARIANT -> ""
        Variance.IN_VARIANCE -> "in"
        Variance.OUT_VARIANCE -> "out"
    }

    override fun TypeParameterDescriptor.name(): String = name.asString()
    override fun TypeParameterDescriptor.upperBounds(): List<KotlinType> = upperBounds
    override fun KotlinType.isNullableAnyOrFlexibleAny(): Boolean = isAnyOrNullableAny() && nullability() != TypeNullability.NOT_NULL
    override fun KotlinType.renderType(): String = DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(this)
}
