// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.hierarchy.calls

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.refactoring.psiElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

class KotlinCallHierarchyProvider : HierarchyProvider {
    override fun getTarget(dataContext: DataContext): KtElement? {
        val element = dataContext.psiElement?.let { getCallHierarchyElement(it) }
        if (element is KtFile) return null
        return element
    }

    override fun createHierarchyBrowser(target: PsiElement) = KotlinCallHierarchyBrowser(target)

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
        (hierarchyBrowser as KotlinCallHierarchyBrowser).changeView(CallHierarchyBrowserBase.getCallerType())
    }
}