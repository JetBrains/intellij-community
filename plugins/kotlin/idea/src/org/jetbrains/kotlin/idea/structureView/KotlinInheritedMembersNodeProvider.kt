// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structureView

import com.intellij.ide.util.InheritedMembersNodeProvider
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi.NavigatablePsiElement
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinInheritedMembersNodeProvider : InheritedMembersNodeProvider<TreeElement>() {
    override fun provideNodes(node: TreeElement): Collection<TreeElement> {
        if (node !is KotlinStructureViewElement) return listOf()

        val element = node.element as? KtClassOrObject ?: return listOf()

        val project = element.project

        val descriptor = element.resolveToDescriptorIfAny(BodyResolveMode.FULL) ?: return listOf()

        val children = ArrayList<TreeElement>()

        val defaultType = descriptor.defaultType
        for (memberDescriptor in defaultType.memberScope.getContributedDescriptors()) {
            if (memberDescriptor !is CallableMemberDescriptor) continue

            when (memberDescriptor.kind) {
                CallableMemberDescriptor.Kind.FAKE_OVERRIDE,
                CallableMemberDescriptor.Kind.DELEGATION -> {
                    val superTypeMember = DescriptorToSourceUtilsIde.getAnyDeclaration(project, memberDescriptor)
                    if (superTypeMember is NavigatablePsiElement) {
                        children.add(KotlinStructureViewElement(superTypeMember, memberDescriptor, true))
                    }
                }
                CallableMemberDescriptor.Kind.DECLARATION -> Unit /* Don't show */
                CallableMemberDescriptor.Kind.SYNTHESIZED -> Unit /* Don't show */
            }
        }

        return children
    }
}
