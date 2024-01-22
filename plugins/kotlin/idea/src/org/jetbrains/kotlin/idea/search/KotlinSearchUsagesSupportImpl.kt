// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search


import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.hasJavaResolutionFacade
import org.jetbrains.kotlin.idea.core.getDirectlyOverriddenDeclarations
import org.jetbrains.kotlin.idea.core.isInheritable
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.search.usagesSearch.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.ImportPath

class KotlinSearchUsagesSupportImpl : KotlinSearchUsagesSupport {
  override fun isInvokeOfCompanionObject(psiReference: PsiReference, searchTarget: KtNamedDeclaration): Boolean {
    return false
  }

    override fun isCallableOverrideUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean =
        reference.isCallableOverrideUsage(declaration)

    override fun isCallableOverride(subDeclaration: KtDeclaration, superDeclaration: PsiNamedElement): Boolean {
        val candidateDescriptor = subDeclaration.unsafeResolveToDescriptor()
        if (candidateDescriptor !is CallableMemberDescriptor) return false

        val overriddenDescriptors = candidateDescriptor.getDirectlyOverriddenDeclarations()
        for (candidateSuper in overriddenDescriptors) {
            val candidateDeclaration = DescriptorToSourceUtils.descriptorToDeclaration(candidateSuper)
            if (candidateDeclaration == superDeclaration) {
                return true
            }

        }
        return false
    }

    override fun isUsageInContainingDeclaration(reference: PsiReference, declaration: KtNamedDeclaration): Boolean =
        reference.isUsageInContainingDeclaration(declaration)

    override fun isExtensionOfDeclarationClassUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean =
        reference.isExtensionOfDeclarationClassUsage(declaration)

    override fun getReceiverTypeSearcherInfo(psiElement: PsiElement, isDestructionDeclarationSearch: Boolean): ReceiverTypeSearcherInfo? =
        psiElement.getReceiverTypeSearcherInfo(isDestructionDeclarationSearch)

    override fun forceResolveReferences(file: KtFile, elements: List<KtElement>) =
        file.forceResolveReferences(elements)

    override fun scriptDefinitionExists(file: PsiFile): Boolean =
        file.scriptDefinitionExists()

    override fun getDefaultImports(file: KtFile): List<ImportPath> =
        file.getDefaultImports()

    override fun forEachKotlinOverride(
        ktClass: KtClass,
        members: List<KtNamedDeclaration>,
        scope: SearchScope,
        searchDeeply: Boolean,
        processor: (superMember: PsiElement, overridingMember: PsiElement) -> Boolean
    ): Boolean =
        org.jetbrains.kotlin.idea.search.declarationsSearch.forEachKotlinOverride(
            ktClass,
            members,
            scope,
            searchDeeply,
            processor
        )

    override fun findSuperMethodsNoWrapping(method: PsiElement, deepest: Boolean): List<PsiElement> =
        org.jetbrains.kotlin.idea.search.declarationsSearch.findSuperMethodsNoWrapping(method, deepest)

    override fun isOverridable(declaration: KtDeclaration): Boolean =
        declaration.isOverridable()

    override fun isInheritable(ktClass: KtClass): Boolean =
        ktClass.isInheritable()

    override fun canBeResolvedWithFrontEnd(element: PsiElement): Boolean =
        element.hasJavaResolutionFacade()

    override fun createConstructorHandle(ktDeclaration: KtDeclaration): KotlinSearchUsagesSupport.ConstructorCallHandle =
        KotlinConstructorCallLazyDescriptorHandle(ktDeclaration)

    override fun createConstructorHandle(psiMethod: PsiMethod): KotlinSearchUsagesSupport.ConstructorCallHandle =
        JavaConstructorCallLazyDescriptorHandle(psiMethod)
}
