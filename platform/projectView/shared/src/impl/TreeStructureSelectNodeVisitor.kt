// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.tree.TreeVisitor
import org.jetbrains.annotations.ApiStatus

/**
 * Matches nodes the way the classic Project View does (see the package-private
 * `com.intellij.ide.projectView.impl.ProjectViewNodeVisitor` / `ProjectViewFileVisitor`),
 * by unwrapping each node's legacy [AbstractTreeNode] from [TreeStructureProjectViewNode.elementDescriptor].
 */
@ApiStatus.Internal
class TreeStructureSelectNodeVisitorProvider : ProjectViewSelectNodeVisitorProvider<TreeStructureProjectViewNode> {
  override fun createSelectNodeVisitor(
    element: PsiElement?,
    file: VirtualFile?,
  ): ProjectViewSelectNodeVisitor<TreeStructureProjectViewNode> = TreeStructureSelectNodeVisitor(element, file)
}

private class TreeStructureSelectNodeVisitor(
  element: PsiElement?,
  file: VirtualFile?,
) : ProjectViewSelectNodeVisitor<TreeStructureProjectViewNode>(element, file) {

  override suspend fun visitNodeForSelect(
    node: BackendProjectViewNodeModel<TreeStructureProjectViewNode>,
  ): TreeVisitor.Action = readAction {
    val treeNode = node.userObject.elementDescriptor as? AbstractTreeNode<*>
                   ?: return@readAction TreeVisitor.Action.SKIP_CHILDREN // not a legacy node, nothing to match against
    val element = element
    val file = file
    when {
      element != null -> visitForElement(treeNode, element, file)
      file != null -> visitForFile(treeNode, file)
      else -> TreeVisitor.Action.SKIP_CHILDREN
    }
  }

  private fun visitForElement(node: AbstractTreeNode<*>, element: PsiElement, file: VirtualFile?): TreeVisitor.Action {
    if (!element.isValid) return TreeVisitor.Action.SKIP_SIBLINGS // the element is gone, abort the search
    return when {
      node.canRepresent(element) -> TreeVisitor.Action.INTERRUPT
      mayContainElement(node, element, file) -> TreeVisitor.Action.CONTINUE
      else -> TreeVisitor.Action.SKIP_CHILDREN
    }
  }

  private fun mayContainElement(node: AbstractTreeNode<*>, element: PsiElement, file: VirtualFile?): Boolean {
    if (!node.mayContain(element)) return false
    if (node is ProjectViewNode<*>) {
      if (file != null && node.contains(file)) return true
      val elementFile = PsiUtilCore.getVirtualFile(element)
      if (elementFile != null && node.contains(elementFile)) return true
    }
    val content = node.value as? PsiElement
    return content != null && PsiTreeUtil.isAncestor(content, element, true)
  }

  private fun visitForFile(node: AbstractTreeNode<*>, file: VirtualFile): TreeVisitor.Action {
    if (!file.isValid) return TreeVisitor.Action.SKIP_CHILDREN
    return when {
      node.canRepresent(file) -> TreeVisitor.Action.INTERRUPT
      mayContainFile(node, file) -> TreeVisitor.Action.CONTINUE
      else -> TreeVisitor.Action.SKIP_CHILDREN
    }
  }

  private fun mayContainFile(node: AbstractTreeNode<*>, file: VirtualFile): Boolean {
    if (node is ProjectViewNode<*> && node.contains(file)) return true
    val content = (node.value as? PsiElement)?.let { PsiUtilCore.getVirtualFile(it) }
    return content != null && VfsUtilCore.isAncestor(content, file, true)
  }
}
