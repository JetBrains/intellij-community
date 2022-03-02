// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticTestRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

class KotlinAutomaticTestRenamerFactory : AutomaticTestRenamerFactory() {
    private fun getPsiClass(element: PsiElement): PsiClass? {
        return when (element) {
            is KtLightClassForSourceDeclaration -> element
            is KtClassOrObject -> element.toLightClass()
            is KtFile -> element.findFacadeClass()
            else -> null
        }
    }

    override fun isApplicable(element: PsiElement): Boolean {
        val psiClass = getPsiClass(element) ?: return false
        return super.isApplicable(psiClass)
    }

    override fun createRenamer(element: PsiElement, newName: String, usages: MutableCollection<UsageInfo>): AutomaticRenamer {
        val psiClass = getPsiClass(element)!!
        val newPsiClassName = if (psiClass is KtLightClassForFacade) PackagePartClassUtils.getFilePartShortName(newName) else newName
        return super.createRenamer(psiClass, newPsiClassName, usages)
    }
}
