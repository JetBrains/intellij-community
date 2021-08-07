// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.psi.*

interface KotlinFindUsagesSupport {

    companion object {
        fun getInstance(project: Project): KotlinFindUsagesSupport = project.getServiceSafe()

        val KtParameter.isDataClassComponentFunction: Boolean
            get() =  getInstance(project).isDataClassComponentFunction(this)

        fun KtElement.isCallReceiverRefersToCompanionObject(companionObject: KtObjectDeclaration): Boolean =
            getInstance(project).isCallReceiverRefersToCompanionObject(this, companionObject)

        fun getTopMostOverriddenElementsToHighlight(target: PsiElement): List<PsiElement> =
            getInstance(target.project).getTopMostOverriddenElementsToHighlight(target)

        fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String? =
            getInstance(declaration.project).tryRenderDeclarationCompactStyle(declaration)

        fun PsiReference.isConstructorUsage(ktClassOrObject: KtClassOrObject): Boolean =
            getInstance(ktClassOrObject.project).isConstructorUsage(this, ktClassOrObject)

        fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?) : List<PsiElement> =
            getInstance(declaration.project).getSuperMethods(declaration, ignore)

        fun sourcesAndLibraries(delegate: GlobalSearchScope, project: Project): GlobalSearchScope =
            getInstance(project).sourcesAndLibraries(delegate, project)
    }

    fun isCallReceiverRefersToCompanionObject(element: KtElement, companionObject: KtObjectDeclaration): Boolean

    fun isDataClassComponentFunction(element: KtParameter): Boolean

    fun getTopMostOverriddenElementsToHighlight(target: PsiElement): List<PsiElement>

    fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String?

    fun isConstructorUsage(psiReference: PsiReference, ktClassOrObject: KtClassOrObject): Boolean

    fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?) : List<PsiElement>

    fun sourcesAndLibraries(delegate: GlobalSearchScope, project: Project): GlobalSearchScope
}