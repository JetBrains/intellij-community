// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiConstructorCall
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

interface KotlinFindUsagesSupport {

    companion object {
        fun getInstance(project: Project): KotlinFindUsagesSupport = project.service()

        val KtParameter.isDataClassComponentFunction: Boolean
            get() = getInstance(project).isDataClassComponentFunction(this)

        fun processCompanionObjectInternalReferences(
            companionObject: KtObjectDeclaration,
            referenceProcessor: Processor<PsiReference>
        ): Boolean =
            getInstance(companionObject.project).processCompanionObjectInternalReferences(companionObject, referenceProcessor)

        fun getTopMostOverriddenElementsToHighlight(target: PsiElement): List<PsiElement> =
            getInstance(target.project).getTopMostOverriddenElementsToHighlight(target)

        fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String? =
            getInstance(declaration.project).tryRenderDeclarationCompactStyle(declaration)

        fun PsiReference.isConstructorUsage(ktClassOrObject: KtClassOrObject): Boolean {
            fun isJavaConstructorUsage(): Boolean {
                val call = element.getNonStrictParentOfType<PsiConstructorCall>()
                return call == element.parent && call?.resolveConstructor()?.containingClass?.navigationElement == ktClassOrObject
            }

            return isJavaConstructorUsage() || getInstance(ktClassOrObject.project).isKotlinConstructorUsage(this, ktClassOrObject)
        }

        fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?) : List<PsiElement> =
            getInstance(declaration.project).getSuperMethods(declaration, ignore)

        fun sourcesAndLibraries(delegate: GlobalSearchScope, project: Project): GlobalSearchScope =
            getInstance(project).sourcesAndLibraries(delegate, project)
    }

    fun processCompanionObjectInternalReferences(companionObject: KtObjectDeclaration, referenceProcessor: Processor<PsiReference>): Boolean

    fun isDataClassComponentFunction(element: KtParameter): Boolean

    fun getTopMostOverriddenElementsToHighlight(target: PsiElement): List<PsiElement>

    fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String?

    fun isKotlinConstructorUsage(psiReference: PsiReference, ktClassOrObject: KtClassOrObject): Boolean

    fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?) : List<PsiElement>

    fun sourcesAndLibraries(delegate: GlobalSearchScope, project: Project): GlobalSearchScope

    fun checkSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?, @Nls actionString: String): List<PsiElement>
}
