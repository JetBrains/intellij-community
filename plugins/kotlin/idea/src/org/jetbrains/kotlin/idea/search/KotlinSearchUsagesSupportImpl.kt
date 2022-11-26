// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.hasJavaResolutionFacade
import org.jetbrains.kotlin.idea.core.getDirectlyOverriddenDeclarations
import org.jetbrains.kotlin.idea.core.isInheritable
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.search.usagesSearch.*
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex
import org.jetbrains.kotlin.idea.util.actualsForExpected
import org.jetbrains.kotlin.idea.util.expectedDeclarationIfAny
import org.jetbrains.kotlin.idea.util.isExpectDeclaration
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.ImportPath

class KotlinSearchUsagesSupportImpl : KotlinSearchUsagesSupport {
    override fun actualsForExpected(declaration: KtDeclaration, module: Module?): Set<KtDeclaration> =
        declaration.actualsForExpected(module)

    override fun dataClassComponentMethodName(element: KtParameter): String? =
        element.dataClassComponentFunction()?.name?.asString()

    override fun hasType(element: KtExpression): Boolean =
        org.jetbrains.kotlin.idea.search.usagesSearch.hasType(element)

    override fun isSamInterface(psiClass: PsiClass): Boolean =
        org.jetbrains.kotlin.idea.search.usagesSearch.isSamInterface(psiClass)

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

    override fun findDeepestSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> =
        org.jetbrains.kotlin.idea.search.declarationsSearch.findDeepestSuperMethodsNoWrapping(method)

    override fun findSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> =
        org.jetbrains.kotlin.idea.search.declarationsSearch.findSuperMethodsNoWrapping(method)

    override fun findTypeAliasByShortName(shortName: String, project: Project, scope: GlobalSearchScope): Collection<KtTypeAlias> =
        KotlinTypeAliasShortNameIndex.get(shortName, project, scope)

    override fun isInProjectSource(element: PsiElement, includeScriptsOutsideSourceRoots: Boolean): Boolean =
        RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = includeScriptsOutsideSourceRoots).matches(element)

    override fun isOverridable(declaration: KtDeclaration): Boolean =
        declaration.isOverridable()

    override fun isInheritable(ktClass: KtClass): Boolean =
        ktClass.isInheritable()

    override fun formatJavaOrLightMethod(method: PsiMethod): String =
        org.jetbrains.kotlin.idea.refactoring.formatJavaOrLightMethod(method)

    override fun formatClass(classOrObject: KtClassOrObject): String =
        org.jetbrains.kotlin.idea.refactoring.formatClass(classOrObject)

    override fun expectedDeclarationIfAny(declaration: KtDeclaration): KtDeclaration? =
        declaration.expectedDeclarationIfAny()

    override fun isExpectDeclaration(declaration: KtDeclaration): Boolean =
        declaration.isExpectDeclaration()

    override fun canBeResolvedWithFrontEnd(element: PsiElement): Boolean =
        element.hasJavaResolutionFacade()

    override fun createConstructorHandle(ktDeclaration: KtDeclaration): KotlinSearchUsagesSupport.ConstructorCallHandle =
        KotlinConstructorCallLazyDescriptorHandle(ktDeclaration)

    override fun createConstructorHandle(psiMethod: PsiMethod): KotlinSearchUsagesSupport.ConstructorCallHandle =
        JavaConstructorCallLazyDescriptorHandle(psiMethod)
}
