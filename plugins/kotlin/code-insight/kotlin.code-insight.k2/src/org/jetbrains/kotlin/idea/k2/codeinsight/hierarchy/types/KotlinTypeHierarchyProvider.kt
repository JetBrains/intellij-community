// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

class KotlinTypeHierarchyProvider : HierarchyProvider {
    override fun getTarget(dataContext: DataContext): KtClassOrObject? {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null

        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        if (editor != null) {
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? KtFile ?: return null

            val targetElement = TargetElementUtil.findTargetElement(
                editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED or
                        TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or
                        TargetElementUtil.LOOKUP_ITEM_ACCEPTED
            )

            if (targetElement is KtClassOrObject) {
                return targetElement.takeIf { targetElement.name != null }
            }

            val elementAtCaret = file.findElementAt(editor.caretModel.offset)
            var element = elementAtCaret
            while (element != null) {

                if (element is KtFile) {
                    return element.declarations.filterIsInstance<KtClassOrObject>().singleOrNull()
                }

                if (element is KtClassOrObject && element.name != null) {
                    return element
                }

                element = element.parent
            }

            return null
        } else {
            return CommonDataKeys.PSI_ELEMENT.getData(dataContext) as? KtClassOrObject
        }
    }

    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser {
        return KotlinTypeHierarchyBrowser(target.getProject(), target as KtClassOrObject)
    }

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
        val browser = hierarchyBrowser as KotlinTypeHierarchyBrowser
        val typeName =
            if (browser.isInterface) TypeHierarchyBrowserBase.getSubtypesHierarchyType() else TypeHierarchyBrowserBase.getTypeHierarchyType()
        browser.changeView(typeName)
    }
}
