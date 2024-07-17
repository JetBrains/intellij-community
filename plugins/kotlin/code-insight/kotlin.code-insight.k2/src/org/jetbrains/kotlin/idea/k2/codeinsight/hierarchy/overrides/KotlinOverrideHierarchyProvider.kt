// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.overrides

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.ide.hierarchy.MethodHierarchyBrowserBase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate

internal class KotlinOverrideHierarchyProvider : HierarchyProvider {
    override fun getTarget(dataContext: DataContext): PsiElement? {
        return CommonDataKeys.PROJECT.getData(dataContext)?.let { project ->
            getOverrideHierarchyElement(getCurrentElement(dataContext, project))
        }
    }

    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser =
        KotlinOverrideHierarchyBrowser(target.project, target)

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
        (hierarchyBrowser as HierarchyBrowserBaseEx).changeView(MethodHierarchyBrowserBase.getMethodType())
    }

    private fun getOverrideHierarchyElement(element: PsiElement?): PsiElement? =
        element?.getParentOfTypesAndPredicate { it.isOverrideHierarchyElement() }
}

private fun getCurrentElement(dataContext: DataContext, project: Project): PsiElement? {
    val editor = CommonDataKeys.EDITOR.getData(dataContext)
    if (editor != null) {
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        if (!RootKindFilter.projectAndLibrarySources.matches(file)) return null
        return TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().allAccepted)
    }

    return CommonDataKeys.PSI_ELEMENT.getData(dataContext)
}

internal fun PsiElement.isOverrideHierarchyElement() = this is KtCallableDeclaration && containingClassOrObject != null
