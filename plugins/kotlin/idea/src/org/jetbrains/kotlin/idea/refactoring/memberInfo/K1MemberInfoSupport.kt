// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.memberInfo

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K1MemberInfoSupport : KotlinMemberInfoSupport {
    override fun getOverrides(member: KtNamedDeclaration): Boolean? {
        val memberDescriptor = member.resolveToDescriptorWrapperAware()
        val overriddenDescriptors = (memberDescriptor as? CallableMemberDescriptor)?.overriddenDescriptors ?: return null
        if (overriddenDescriptors.isNotEmpty()) {
            return overriddenDescriptors.any { it.modality != Modality.ABSTRACT }
        }
        return null
    }
}