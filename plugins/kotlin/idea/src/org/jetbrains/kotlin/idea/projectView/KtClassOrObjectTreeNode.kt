// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.projectView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.structureView.getStructureDeclarations
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPsiUtil

class KtClassOrObjectTreeNode(project: Project?, ktClassOrObject: KtClassOrObject, viewSettings: ViewSettings) :
    AbstractPsiBasedNode<KtClassOrObject>(project, ktClassOrObject, viewSettings) {

    override fun extractPsiFromValue(): PsiElement? = value

    override fun getChildrenImpl(): Collection<AbstractTreeNode<*>>? {
        return if (value != null && settings.isShowMembers) {
            value.getStructureDeclarations().map { declaration ->
                if (declaration is KtClassOrObject)
                    KtClassOrObjectTreeNode(project, declaration, settings)
                else
                    KtDeclarationTreeNode(project, declaration, settings)
            }
        } else {
            emptyList()
        }
    }

    override fun updateImpl(data: PresentationData) {
        value?.let {
            data.presentableText = KtDeclarationTreeNode.tryGetRepresentableText(it)
        }
    }

    override fun isDeprecated() = KtPsiUtil.isDeprecated(value)

    override fun canRepresent(element: Any?): Boolean {
        if (!isValid) {
            return false
        }

        return super.canRepresent(element) || canRepresentPsiElement(element)
    }

    private fun canRepresentPsiElement(element: Any?): Boolean {
        if (value == null || !value.isValid) {
            return false
        } else if (value === element) {
            return true
        }

        val file = value.containingFile
        return when (element) {
            file -> true
            is VirtualFile -> element == file.virtualFile
            is PsiElement -> !settings.isShowMembers && file == element.containingFile
            else -> false
        }
    }

    override fun expandOnDoubleClick(): Boolean = false

    override fun getWeight() = 20
}
