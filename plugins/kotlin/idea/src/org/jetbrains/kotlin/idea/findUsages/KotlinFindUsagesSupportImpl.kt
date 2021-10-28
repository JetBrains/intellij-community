// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.search.usagesSearch.dataClassComponentFunction
import org.jetbrains.kotlin.idea.search.usagesSearch.isCallReceiverRefersToCompanionObject
import org.jetbrains.kotlin.idea.search.usagesSearch.isConstructorUsage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class KotlinFindUsagesSupportImpl : KotlinFindUsagesSupport {
    override fun processCompanionObjectInternalReferences(
        companionObject: KtObjectDeclaration,
        referenceProcessor: Processor<PsiReference>
    ): Boolean {
        val klass = companionObject.getStrictParentOfType<KtClass>() ?: return true
        return !klass.anyDescendantOfType(fun(element: KtElement): Boolean {
            if (element == companionObject) return false // skip companion object itself
            return if (isCallReceiverRefersToCompanionObject(element, companionObject)) {
                element.references.any { !referenceProcessor.process(it) }
            } else false
        })
    }

    override fun isDataClassComponentFunction(element: KtParameter): Boolean =
        element.dataClassComponentFunction() != null

    override fun getTopMostOverriddenElementsToHighlight(target: PsiElement): List<PsiElement> =
        org.jetbrains.kotlin.idea.search.usagesSearch.getTopMostOverriddenElementsToHighlight(target)

    override fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String? =
        org.jetbrains.kotlin.idea.search.usagesSearch.tryRenderDeclarationCompactStyle(declaration)

    override fun isConstructorUsage(psiReference: PsiReference, ktClassOrObject: KtClassOrObject): Boolean =
        psiReference.isConstructorUsage(ktClassOrObject)

    override fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?): List<PsiElement> =
        org.jetbrains.kotlin.idea.refactoring.getSuperMethods(declaration, ignore)

    override fun sourcesAndLibraries(delegate: GlobalSearchScope, project: Project): GlobalSearchScope =
        org.jetbrains.kotlin.idea.stubindex.KotlinSourceFilterScope.sourcesAndLibraries(delegate, project)
}