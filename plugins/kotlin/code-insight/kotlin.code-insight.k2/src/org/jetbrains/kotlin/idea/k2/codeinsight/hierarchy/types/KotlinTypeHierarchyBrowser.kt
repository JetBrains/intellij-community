// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.JavaHierarchyUtil
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.ide.hierarchy.type.TypeHierarchyNodeDescriptor
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewLongNameLocation
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import java.text.MessageFormat
import javax.swing.JPanel
import javax.swing.JTree

class KotlinTypeHierarchyBrowser(project: Project, klass: KtClassOrObject) : TypeHierarchyBrowserBase(project, klass) {
    override fun isInterface(psiElement: PsiElement): Boolean {
        return psiElement is KtClass && (psiElement.isInterface() || psiElement.isAnnotation())
    }

    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        createTreeAndSetupCommonActions(trees, IdeActions.GROUP_TYPE_HIERARCHY_POPUP)
    }

    override fun prependActions(actionGroup: DefaultActionGroup) {
        super.prependActions(actionGroup)
        actionGroup.add(object : ChangeScopeAction() {
            override fun isEnabled(): Boolean {
                return !Comparing.strEqual(getCurrentViewType(), getSupertypesHierarchyType())
            }
        })
    }

    override fun getContentDisplayName(typeName: String, element: PsiElement): String {
        return MessageFormat.format(typeName, ElementDescriptionUtil.getElementDescription(element, UsageViewLongNameLocation.INSTANCE))
    }

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? {
        return (descriptor as? KotlinTypeHierarchyNodeDescriptor)?.psiElement ?: (descriptor as? TypeHierarchyNodeDescriptor)?.psiElement
    }

    override fun createLegendPanel(): JPanel? {
        return null
    }

    override fun isApplicableElement(element: PsiElement): Boolean {
        return element is KtClassOrObject
    }

    override fun getComparator(): Comparator<NodeDescriptor<*>?> {
        return JavaHierarchyUtil.getComparator(myProject)
    }

    override fun createHierarchyTreeStructure(typeName: String, psiElement: PsiElement): HierarchyTreeStructure? {
        when (typeName) {
            getSupertypesHierarchyType() -> {
                return KotlinSupertypesHierarchyTreeStructure(myProject, psiElement as KtClassOrObject)
            }

            getSubtypesHierarchyType() -> {
                return KotlinSubtypesHierarchyTreeStructure(myProject, psiElement as KtClassOrObject, getCurrentScopeType())
            }

            getTypeHierarchyType() -> {
                return KotlinTypeHierarchyTreeStructure(myProject, psiElement as KtClassOrObject, getCurrentScopeType())
            }

            else -> {
                LOG.error("unexpected type: $typeName")
                return null
            }
        }
    }

    override fun canBeDeleted(psiElement: PsiElement?): Boolean {
        return psiElement is KtClassOrObject && psiElement.name != null || psiElement is PsiClass && psiElement.name != null
    }

    override fun getQualifiedName(psiElement: PsiElement?): String {
        return (psiElement as? KtClassOrObject)?.fqName?.asString() ?: (psiElement as? PsiClass)?.qualifiedName ?: ""
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinTypeHierarchyBrowser::class.java)
    }
}