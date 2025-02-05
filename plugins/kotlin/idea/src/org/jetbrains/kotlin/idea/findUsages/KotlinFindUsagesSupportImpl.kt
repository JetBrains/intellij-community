// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchOverriders
import org.jetbrains.kotlin.idea.search.usagesSearch.isCallReceiverRefersToCompanionObject
import org.jetbrains.kotlin.idea.search.usagesSearch.isKotlinConstructorUsage
import org.jetbrains.kotlin.idea.util.KotlinPsiDeclarationRenderer
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy

private val FUNCTION_RENDERER = DescriptorRenderer.withOptions {
    withDefinedIn = false
    modifiers = emptySet()
    classifierNamePolicy = ClassifierNamePolicy.SHORT
    withoutTypeParameters = true
    parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
}

class KotlinFindUsagesSupportImpl : KotlinFindUsagesSupport {
    override fun processCompanionObjectInternalReferences(
        companionObject: KtObjectDeclaration,
        referenceProcessor: Processor<PsiReference>
    ): Boolean {
        val klass = companionObject.getStrictParentOfType<KtClass>() ?: return true
        if (klass.containingKtFile.isCompiled) return true
        return !klass.anyDescendantOfType(fun(element: KtElement): Boolean {
            if (element == companionObject) return false // skip companion object itself
            return if (isCallReceiverRefersToCompanionObject(element, companionObject)) {
                element.references.any { !referenceProcessor.process(it) }
            } else false
        })
    }

    override fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String? =
        org.jetbrains.kotlin.idea.search.usagesSearch.tryRenderDeclarationCompactStyle(declaration)

    override fun renderDeclaration(method: KtDeclaration): String =
        KotlinPsiDeclarationRenderer.render(method) ?: FUNCTION_RENDERER.render(method.unsafeResolveToDescriptor())

    override fun isKotlinConstructorUsage(psiReference: PsiReference, ktClassOrObject: KtClassOrObject): Boolean =
        psiReference.isKotlinConstructorUsage(ktClassOrObject)

    override fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?): List<PsiElement> =
        org.jetbrains.kotlin.idea.refactoring.getSuperMethods(declaration, ignore)

    override fun searchOverriders(
        element: PsiElement,
        searchScope: SearchScope,
    ): Sequence<PsiElement> = HierarchySearchRequest(element, searchScope).searchOverriders().asIterable().asSequence()

    override fun searchInheritors(
        element: PsiElement,
        searchScope: SearchScope,
        searchDeeply: Boolean,
    ): Sequence<PsiElement> = HierarchySearchRequest(element, searchScope, searchDeeply).searchInheritors().asIterable().asSequence()
}
