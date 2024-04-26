// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.util.liftToExpected
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

fun DeclarationDescriptor.findKDoc(
    descriptorToPsi: (DeclarationDescriptorWithSource) -> PsiElement? = { DescriptorToSourceUtils.descriptorToDeclaration(it) }
): KDocContent? {
    if (this is DeclarationDescriptorWithSource) {
        val psiDeclaration = descriptorToPsi(this)?.navigationElement
        return (psiDeclaration as? KtElement)?.findKDoc(descriptorToPsi)
    }
    return null
}

private typealias DescriptorToPsi = (DeclarationDescriptorWithSource) -> PsiElement?

fun KtElement.findKDoc(descriptorToPsi: DescriptorToPsi): KDocContent? {
    return findKDocByPsi()
        ?: this.lookupInheritedKDoc(descriptorToPsi)
}

private fun KtElement.lookupInheritedKDoc(descriptorToPsi: DescriptorToPsi): KDocContent? {
    if (this is KtDeclaration) {
        val descriptor = resolveToDescriptorIfAny()

        if (descriptor is CallableDescriptor) {
            for (baseDescriptor in descriptor.overriddenDescriptors) {
                val baseKDoc = baseDescriptor.original.findKDoc(descriptorToPsi)
                if (baseKDoc != null) {
                    return baseKDoc
                }
            }
        }

        val expectedKDoc = descriptor?.liftToExpected().takeIf { it != descriptor }?.findKDoc(descriptorToPsi)

        if (expectedKDoc != null) {
            return expectedKDoc
        }
    }

    return null
}