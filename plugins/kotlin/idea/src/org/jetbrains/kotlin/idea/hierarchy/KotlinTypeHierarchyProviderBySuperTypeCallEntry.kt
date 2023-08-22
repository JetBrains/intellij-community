// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.hierarchy

import com.intellij.ide.hierarchy.type.JavaTypeHierarchyProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch

class KotlinTypeHierarchyProviderBySuperTypeCallEntry : JavaTypeHierarchyProvider() {
    override fun getTarget(dataContext: DataContext): PsiClass? {
        val project = PlatformDataKeys.PROJECT.getData(dataContext) ?: return null
        val editor = PlatformDataKeys.EDITOR.getData(dataContext) ?: return null

        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null

        val offset = editor.caretModel.offset
        val elementAtCaret = file.findElementAt(offset) ?: return null
        if (elementAtCaret.getParentOfTypeAndBranch<KtSuperTypeCallEntry> { calleeExpression } == null) return null

        val targetElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext)?.unwrapped
        return when {
            targetElement is KtConstructor<*> -> targetElement.containingClassOrObject?.toLightClass()
            targetElement is PsiMethod && targetElement.isConstructor -> targetElement.containingClass
            targetElement is KtClassOrObject -> targetElement.toLightClass()
            targetElement is PsiClass -> targetElement
            else -> null
        }
    }
}
