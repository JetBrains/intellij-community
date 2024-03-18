// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiConstructorCall
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

interface KotlinFindUsagesSupport {

    companion object {
        fun getInstance(project: Project): KotlinFindUsagesSupport = project.service()

        fun processCompanionObjectInternalReferences(
            companionObject: KtObjectDeclaration,
            referenceProcessor: Processor<PsiReference>
        ): Boolean =
            getInstance(companionObject.project).processCompanionObjectInternalReferences(companionObject, referenceProcessor)

        fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String? =
            getInstance(declaration.project).tryRenderDeclarationCompactStyle(declaration)

        @NlsSafe
        fun formatJavaOrLightMethod(method: PsiMethod): String =
            getInstance(method.project).formatJavaOrLightMethod(method)

        fun PsiReference.isConstructorUsage(ktClassOrObject: KtClassOrObject): Boolean {
            fun isJavaConstructorUsage(): Boolean {
                val call = element.getNonStrictParentOfType<PsiConstructorCall>()
                return call == element.parent && call?.resolveConstructor()?.containingClass?.navigationElement == ktClassOrObject
            }

            return isJavaConstructorUsage() || getInstance(ktClassOrObject.project).isKotlinConstructorUsage(this, ktClassOrObject)
        }

        fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?) : List<PsiElement> =
            getInstance(declaration.project).getSuperMethods(declaration, ignore)
    }

    fun processCompanionObjectInternalReferences(companionObject: KtObjectDeclaration, referenceProcessor: Processor<PsiReference>): Boolean

    fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String?

    fun formatJavaOrLightMethod(method: PsiMethod): String

    fun isKotlinConstructorUsage(psiReference: PsiReference, ktClassOrObject: KtClassOrObject): Boolean

    fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?) : List<PsiElement>

}
