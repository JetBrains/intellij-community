// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.OverloadChecker

internal class K1MemberInfoStorageSupport : KotlinMemberInfoStorageSupport {

    @OptIn(FrontendInternals::class)
    override fun memberConflict(
        member1: KtNamedDeclaration,
        member: KtNamedDeclaration
    ): Boolean {
        val descriptor1 = member1.resolveToDescriptorWrapperAware()
        val descriptor = member.resolveToDescriptorWrapperAware()
        if (descriptor1.name != descriptor.name) return false

        return when {
            descriptor1 is FunctionDescriptor && descriptor is FunctionDescriptor -> {
                val overloadUtil = member1.getResolutionFacade().frontendService<OverloadChecker>()
                !overloadUtil.isOverloadable(descriptor1, descriptor)
            }

            descriptor1 is PropertyDescriptor && descriptor is PropertyDescriptor ||
                    descriptor1 is ClassDescriptor && descriptor is ClassDescriptor -> true

            else -> false
        }
    }

    override fun isInheritor(baseClass: PsiNamedElement, aClass: PsiNamedElement): Boolean {
        val baseDescriptor = baseClass.getClassDescriptorIfAny() ?: return false
        val currentDescriptor = aClass.getClassDescriptorIfAny() ?: return false
        return DescriptorUtils.isSubclass(currentDescriptor, baseDescriptor)
    }
}
