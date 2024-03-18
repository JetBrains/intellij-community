// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.search.usagesSearch.isCallReceiverRefersToCompanionObject
import org.jetbrains.kotlin.idea.search.usagesSearch.isKotlinConstructorUsage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

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

    override fun formatJavaOrLightMethod(method: PsiMethod): String =
        org.jetbrains.kotlin.idea.refactoring.formatJavaOrLightMethod(method)

    override fun isKotlinConstructorUsage(psiReference: PsiReference, ktClassOrObject: KtClassOrObject): Boolean =
        psiReference.isKotlinConstructorUsage(ktClassOrObject)

    override fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?): List<PsiElement> =
        org.jetbrains.kotlin.idea.refactoring.getSuperMethods(declaration, ignore)

}
