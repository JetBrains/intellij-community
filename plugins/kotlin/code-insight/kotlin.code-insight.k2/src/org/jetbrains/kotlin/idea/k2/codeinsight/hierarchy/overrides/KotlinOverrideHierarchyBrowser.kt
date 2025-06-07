// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.overrides

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.JavaHierarchyUtil
import com.intellij.ide.hierarchy.MethodHierarchyBrowserBase
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.ui.PopupHandler
import com.intellij.usageView.UsageViewLongNameLocation
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import javax.swing.JPanel
import javax.swing.JTree

internal class KotlinOverrideHierarchyBrowser(
    project: Project, baseElement: PsiElement
) : MethodHierarchyBrowserBase(project, baseElement) {
    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        val actionManager = ActionManager.getInstance()

        val tree = createTree(false)

        PopupHandler.installPopupMenu(tree, IdeActions.GROUP_METHOD_HIERARCHY_POPUP, ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP)

        BaseOnThisMethodAction().registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_METHOD_HIERARCHY).shortcutSet, tree)

        trees[getMethodType()] = tree
    }

    override fun createLegendPanel(): JPanel? =
        createStandardLegendPanel(
            KotlinBundle.message("hierarchy.legend.member.is.defined.in.class"),
            KotlinBundle.message("hierarchy.legend.member.defined.in.superclass"),
            KotlinBundle.message("hierarchy.legend.member.should.be.defined")
        )

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor) = descriptor.psiElement

    override fun isApplicableElement(psiElement: PsiElement): Boolean =
        psiElement.isOverrideHierarchyElement()

    override fun createHierarchyTreeStructure(typeName: String, psiElement: PsiElement): HierarchyTreeStructure? =
        if (typeName == getMethodType()) KotlinOverrideTreeStructure(myProject, psiElement.originalElement as KtCallableDeclaration) else null

    override fun getComparator() = JavaHierarchyUtil.getComparator(myProject)

    override fun getContentDisplayName(typeName: String, element: PsiElement): String? {
        val targetElement = element.unwrapped
        if (targetElement is KtDeclaration) {
            return ElementDescriptionUtil.getElementDescription(targetElement, UsageViewLongNameLocation.INSTANCE)
        }
        return super.getContentDisplayName(typeName, element)
    }
}
