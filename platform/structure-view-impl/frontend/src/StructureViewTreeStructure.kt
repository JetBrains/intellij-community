// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.platform.structureView.frontend.uiModel.StructureUiModel
import com.intellij.psi.PsiDocumentManager

open class StructureViewTreeStructure(private val project: Project, private val model: StructureUiModel): AbstractTreeStructure() {
  val myRootElement: StructureViewTreeElement by lazy { createRootElement() }

  override fun getRootElement(): StructureViewTreeElement {
    return myRootElement
  }

  override fun getChildElements(element: Any): Array<out Any?> {
    return (element as StructureViewTreeElement).children.toTypedArray()
  }

  override fun getParentElement(element: Any): Any? {
    return (element as StructureViewTreeElement).parent
  }

  override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> {
    return element as StructureViewTreeElement
  }

  override fun commit() {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
  }

  override fun hasSomethingToCommit(): Boolean {
    return PsiDocumentManager.getInstance(project).hasUncommitedDocuments()
  }

  override fun asyncCommit(): ActionCallback {
    return asyncCommitDocuments(project)
  }

  override fun isAlwaysLeaf(element: Any): Boolean {
    return (element as StructureViewTreeElement).value.alwaysLeaf
  }

  open fun rebuildTree() {
    val rootElement = rootElement
    rootElement.rebuildChildren()
    rootElement.update()
  }

  private fun createRootElement(): StructureViewTreeElement {
    return StructureViewTreeElement(project, model.rootElement, model)
  }
}