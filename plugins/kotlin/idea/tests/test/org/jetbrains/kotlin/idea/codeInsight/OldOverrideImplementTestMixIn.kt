// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.core.overrideImplement.AbstractGenerateMembersHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMemberChooserObject
import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMembersHandler
import org.jetbrains.kotlin.psi.KtClassOrObject

interface OldOverrideImplementTestMixIn : OverrideImplementTestMixIn<OverrideMemberChooserObject> {
    override fun createImplementMembersHandler(): AbstractGenerateMembersHandler<OverrideMemberChooserObject> = ImplementMembersHandler()

    override fun createOverrideMembersHandler(): AbstractGenerateMembersHandler<OverrideMemberChooserObject> = OverrideMembersHandler()

    override fun isMemberOfAny(parentClass: KtClassOrObject, chooserObject: OverrideMemberChooserObject): Boolean =
        (chooserObject.descriptor.containingDeclaration as? ClassDescriptor)?.let {
            KotlinBuiltIns.isAny(it)
        } ?: true

    override fun getMemberName(parentClass: KtClassOrObject, chooserObject: OverrideMemberChooserObject): String =
        chooserObject.descriptor.name.asString()

    override fun getContainingClassName(parentClass: KtClassOrObject, chooserObject: OverrideMemberChooserObject): String {
        return chooserObject.immediateSuper.containingDeclaration.name.asString()
    }
}