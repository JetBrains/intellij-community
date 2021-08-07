// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

fun DeclarationDescriptor.findKDoc(
    descriptorToPsi: (DeclarationDescriptorWithSource) -> PsiElement? = { DescriptorToSourceUtils.descriptorToDeclaration(it) }
): KDocTag? {
    if (this is DeclarationDescriptorWithSource) {
        val psiDeclaration = descriptorToPsi(this)?.navigationElement
        return (psiDeclaration as? KtElement)?.findKDoc(descriptorToPsi)
    }
    return null
}

private typealias DescriptorToPsi = (DeclarationDescriptorWithSource) -> PsiElement?

fun KtElement.findKDoc(descriptorToPsi: DescriptorToPsi): KDocTag? {
    return this.lookupOwnedKDoc()
        ?: this.lookupKDocInContainer()
        ?: this.lookupInheritedKDoc(descriptorToPsi)
}

private fun KtElement.lookupOwnedKDoc(): KDocTag? {

    // KDoc for primary constructor is located inside of its class KDoc
    val psiDeclaration = when (this) {
        is KtPrimaryConstructor -> getContainingClassOrObject()
        else -> this
    }

    if (psiDeclaration is KtDeclaration) {
        val kdoc = psiDeclaration.docComment
        if (kdoc != null) {
            if (this is KtConstructor<*>) {
                // ConstructorDescriptor resolves to the same JetDeclaration
                val constructorSection = kdoc.findSectionByTag(KDocKnownTag.CONSTRUCTOR)
                if (constructorSection != null) {
                    return constructorSection
                }
            }
            return kdoc.getDefaultSection()
        }
    }
    return null
}

private fun KtElement.lookupKDocInContainer(): KDocTag? {

    val subjectName = name
    val containingDeclaration =
        PsiTreeUtil.findFirstParent(this, true) {
            it is KtDeclarationWithBody && it !is KtPrimaryConstructor
                    || it is KtClassOrObject
        }

    val containerKDoc = containingDeclaration?.getChildOfType<KDoc>()
    if (containerKDoc == null || subjectName == null) return null
    val propertySection = containerKDoc.findSectionByTag(KDocKnownTag.PROPERTY, subjectName)
    val paramTag = containerKDoc.findDescendantOfType<KDocTag> { it.knownTag == KDocKnownTag.PARAM && it.getSubjectName() == subjectName }


    return when {
        this is KtParameter && this.isPropertyParameter() -> propertySection ?: paramTag
        this is KtParameter || this is KtTypeParameter -> paramTag
        this is KtProperty && containingDeclaration is KtClassOrObject -> propertySection
        else -> null
    }
}


private fun KtElement.lookupInheritedKDoc(descriptorToPsi: DescriptorToPsi): KDocTag? {
    if (this is KtCallableDeclaration) {
        val descriptor = this.resolveToDescriptorIfAny() as? CallableDescriptor ?: return null

        for (baseDescriptor in descriptor.overriddenDescriptors) {
            val baseKDoc = baseDescriptor.original.findKDoc(descriptorToPsi)
            if (baseKDoc != null) {
                return baseKDoc
            }
        }
    }
    return null
}