// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.hierarchy.calls

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.JavaHierarchyUtil
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.ui.PopupHandler
import org.jetbrains.kotlin.psi.KtElement
import javax.swing.JTree

class KotlinCallHierarchyBrowser(element: PsiElement) :
    CallHierarchyBrowserBase(element.project, element) {
    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        val baseOnThisMethodAction = BaseOnThisMethodAction()

        val tree1 = createTree(false)
        PopupHandler.installPopupMenu(
            tree1,
            IdeActions.GROUP_CALL_HIERARCHY_POPUP,
            ActionPlaces.CALL_HIERARCHY_VIEW_POPUP
        )
        baseOnThisMethodAction.registerCustomShortcutSet(
            ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).shortcutSet,
            tree1
        )
        trees[getCalleeType()] = tree1

        val tree2 = createTree(false)
        PopupHandler.installPopupMenu(
            tree2,
            IdeActions.GROUP_CALL_HIERARCHY_POPUP,
            ActionPlaces.CALL_HIERARCHY_VIEW_POPUP
        )
        baseOnThisMethodAction.registerCustomShortcutSet(
            ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).shortcutSet,
            tree2
        )
        trees[getCallerType()] = tree2
    }

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? {
        return getTargetElement(descriptor)
    }

    override fun isApplicableElement(element: PsiElement): Boolean {
        return if (element is PsiClass) false else isCallHierarchyElement(element) // PsiClass is not allowed at the hierarchy root
    }

    override fun createHierarchyTreeStructure(
        type: String,
        psiElement: PsiElement
    ): HierarchyTreeStructure? {
        if (psiElement !is KtElement) return null
        return when (type) {
            getCallerType() -> KotlinCallerTreeStructure(psiElement, currentScopeType)
            getCalleeType() -> KotlinCalleeTreeStructure(psiElement, currentScopeType)
            else -> null
        }
    }

    override fun getComparator(): Comparator<NodeDescriptor<*>> {
        return JavaHierarchyUtil.getComparator(myProject)
    }

    companion object {
        private fun getTargetElement(descriptor: HierarchyNodeDescriptor): PsiElement? {
            return (descriptor as? KotlinCallHierarchyNodeDescriptor)?.psiElement
        }
    }
}