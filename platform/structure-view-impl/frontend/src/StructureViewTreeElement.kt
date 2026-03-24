// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.platform.structureView.frontend.uiModel.FilterTreeAction
import com.intellij.platform.structureView.frontend.uiModel.NodeProviderTreeAction
import com.intellij.platform.structureView.frontend.uiModel.StructureUiModel
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
import org.jetbrains.annotations.Unmodifiable

class StructureViewTreeElement(project: Project, nodeModel: StructureUiTreeElement, val viewModel: StructureUiModel): AbstractTreeNode<StructureUiTreeElement>(project, nodeModel) {
  private var myChildren: MutableList<StructureViewTreeElement>? = null
  private var myOldChildren: MutableList<StructureViewTreeElement>? = null

  override fun getChildren(): @Unmodifiable Collection<AbstractTreeNode<*>?> {
    ensureChildrenAreInitialized()
    return myChildren!!
  }

  override fun getFileStatus(): FileStatus {
    return value.fileStatus
  }

  private fun ensureChildrenAreInitialized() {
    if (myChildren == null) {
      try {
        myChildren = mutableListOf()
        initChildren()
        filterChildren()
        myChildren!!.sortBy { it.value.indexInParent }
        synchronizeChildren()
      }
      catch (pce: IndexNotReadyException) {
        myChildren = null
        throw pce
      }
      catch (pce: ProcessCanceledException) {
        myChildren = null
        throw pce
      }
    }
  }

  private fun setChildren(children: Collection<StructureViewTreeElement>) {
    clearChildren()
    myChildren!!.addAll(children)
  }

  private fun filterChildren() {
    setChildren(myChildren!!.filter {
      for (action in viewModel.getActions()) {
        if (action !is FilterTreeAction || !viewModel.isActionEnabled(action)) continue
        if (!action.isVisible(it.value)) {
          return@filter false
        }
      }
      return@filter true
    })
  }

  fun initChildren() {
    clearChildren()
    val children = value.children.map { StructureViewTreeElement(project, it, viewModel) }

    for (node in children) {
      myChildren!!.add(node)
      node.parent = this
    }

    for (action in viewModel.getActions()) {
      if (action !is NodeProviderTreeAction || !viewModel.isActionEnabled(action)) continue

      val nodes = action.getNodes(value)
      for (node in nodes) {
        val element = StructureViewTreeElement(project, node, viewModel)
        myChildren!!.add(element)
        element.parent = this
      }
    }
  }

  fun synchronizeChildren() {
    val children = myChildren
    if (myOldChildren != null && children != null) {
      val oldValuesToChildrenMap = HashMap<Any, StructureViewTreeElement>()
      for (oldChild in myOldChildren) {
        oldValuesToChildrenMap[oldChild.value] = oldChild
      }

      for (i in children.indices) {
        val newChild = children[i]
        val newValue = newChild.value
        if (newValue != null) {
          val oldChild = oldValuesToChildrenMap[newValue]
          if (oldChild != null) {
            oldChild.value = newValue
            children[i] = oldChild
          }
        }
      }

      myOldChildren = null
    }
  }

  override fun navigate(requestFocus: Boolean) {}

  override fun canNavigate(): Boolean = false

  override fun canNavigateToSource(): Boolean = false

  override fun isAutoExpandAllowed(): Boolean = value.shouldAutoExpand

  override fun isAlwaysShowPlus(): Boolean {
    return value.alwaysShowPlus
  }

  override fun isAlwaysLeaf(): Boolean {
    return value.alwaysLeaf
  }

  fun clearChildren() {
    if (myChildren != null) {
      myChildren!!.clear()
    }
    else {
      myChildren = mutableListOf()
    }
  }

  fun rebuildChildren() {
    if (myChildren != null) {
      myOldChildren = myChildren
      for (node in myChildren) {
        node.rebuildChildren()
      }
      myChildren = null
    }
  }

  override fun update(presentation: PresentationData) {
    presentation.updateFrom(value.presentation)
  }
}