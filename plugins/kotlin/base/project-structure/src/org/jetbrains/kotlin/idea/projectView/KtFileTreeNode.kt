// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

class KtFileTreeNode(
    project: Project?,
    val ktFile: KtFile,
    viewSettings: ViewSettings,
    private val mandatoryChildren: Collection<AbstractTreeNode<*>>
) : PsiFileNode(project, ktFile, viewSettings) {

    override fun getChildrenImpl(): Collection<AbstractTreeNode<*>> =
        if (settings.isShowMembers) {
            mandatoryChildren + ktFile.toDeclarationsNodes(settings)
        } else {
            mandatoryChildren
        }
}

internal fun KtFile.toDeclarationsNodes(settings: ViewSettings): Collection<AbstractPsiBasedNode<out KtDeclaration?>> =
    ((if (isScript()) script else this)?.declarations)?.toNodes(settings) ?: emptyList()

internal fun Collection<KtDeclaration>.toNodes(settings: ViewSettings): Collection<AbstractPsiBasedNode<out KtDeclaration?>> =
    mapNotNull {
        val project = it.project
        if (it is KtClassOrObject) {
            KtClassOrObjectTreeNode(project, it, settings)
        } else {
            KtDeclarationTreeNode.create(project, it, settings)
        }
    }
