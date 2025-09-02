// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.idea.refactoring.memberInfo.getClassDescriptorIfAny
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.utils.collectDescriptorsFiltered
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.utils.keysToMapExceptNulls

class KotlinPullUpData(
    val sourceClass: KtClassOrObject,
    val targetClass: PsiNamedElement,
    val membersToMove: Collection<KtNamedDeclaration>
) {
    val resolutionFacade: ResolutionFacade = sourceClass.getResolutionFacade()

    val sourceClassContext: BindingContext = resolutionFacade.analyzeWithAllCompilerChecks(sourceClass).bindingContext

    val sourceClassDescriptor: ClassDescriptor = sourceClassContext[BindingContext.DECLARATION_TO_DESCRIPTOR, sourceClass] as ClassDescriptor

    val memberDescriptors: Map<KtNamedDeclaration, DeclarationDescriptor> = membersToMove.keysToMapExceptNulls {
        when (it) {
            is KtPsiClassWrapper -> it.psiClass.getJavaClassDescriptor(resolutionFacade)
            is KtParameter -> sourceClassContext[BindingContext.PRIMARY_CONSTRUCTOR_PARAMETER, it]
            else -> sourceClassContext[BindingContext.DECLARATION_TO_DESCRIPTOR, it]
        }
    }

    val targetClassDescriptor: ClassDescriptor = targetClass.getClassDescriptorIfAny(resolutionFacade)!!

    val superEntryForTargetClass: KtSuperTypeListEntry? = sourceClass.getSuperTypeEntryByDescriptor(targetClassDescriptor, sourceClassContext)

    val targetClassSuperResolvedCall: ResolvedCall<out CallableDescriptor>? = superEntryForTargetClass.getResolvedCall(sourceClassContext)

    private val typeParametersInSourceClassContext: List<TypeParameterDescriptor> by lazy {
        sourceClassDescriptor.declaredTypeParameters + sourceClass.getResolutionScope(sourceClassContext, resolutionFacade)
            .collectDescriptorsFiltered(DescriptorKindFilter.NON_SINGLETON_CLASSIFIERS)
            .filterIsInstance<TypeParameterDescriptor>()
    }

    val sourceToTargetClassSubstitutor: TypeSubstitutor by lazy {
        val substitution = LinkedHashMap<TypeConstructor, TypeProjection>()

        typeParametersInSourceClassContext.forEach {
            substitution[it.typeConstructor] = TypeProjectionImpl(TypeIntersector.getUpperBoundsAsType(it))
        }

        val superClassSubstitution = getTypeSubstitution(targetClassDescriptor.defaultType, sourceClassDescriptor.defaultType) ?: emptyMap()
        for ((typeConstructor, typeProjection) in superClassSubstitution) {
            val subClassTypeParameter = typeProjection.type.constructor.declarationDescriptor as? TypeParameterDescriptor
                ?: continue
            val superClassTypeParameter = typeConstructor.declarationDescriptor
                ?: continue
            substitution[subClassTypeParameter.typeConstructor] = TypeProjectionImpl(superClassTypeParameter.defaultType)
        }

        TypeSubstitutor.create(substitution)
    }

    val isInterfaceTarget: Boolean = targetClassDescriptor.kind == ClassKind.INTERFACE
}
