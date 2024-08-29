// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl

import com.intellij.ide.structureView.logical.model.LogicalStructureAssembledModel
import com.intellij.ide.structureView.*
import com.intellij.ide.util.treeView.smartTree.Grouper
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

@Service(Service.Level.PROJECT)
class LogicalStructureViewService(
  private val project: Project
) {

  companion object {
    fun getInstance(project: Project): LogicalStructureViewService = project.getService(LogicalStructureViewService::class.java)
  }

  fun getLogicalStructureBuilder(psiFile: PsiFile): StructureViewBuilder? {
    val assembledModel = LogicalStructureAssembledModel(project, psiFile)
    if (assembledModel.getChildren().isEmpty()) return null
    return object: TreeBasedStructureViewBuilder() {
      override fun createStructureViewModel(editor: Editor?): StructureViewModel {
        return LogicalStructureViewModel(psiFile, editor, assembledModel)
      }

      override fun isRootNodeShown(): Boolean {
        return false
      }
    }
  }

}

private class LogicalStructureViewModel(psiFile: PsiFile, editor: Editor?, assembledModel: LogicalStructureAssembledModel<*>)
  : StructureViewModelBase(psiFile, editor, createViewTreeElement(assembledModel)),
    StructureViewModel.ElementInfoProvider, StructureViewModel.ExpandInfoProvider {

  override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean {
    return false
  }

  override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean {
    return false
  }

  override fun getGroupers(): Array<Grouper> {
    return arrayOf(LogicalGrouper())
  }

  override fun isAutoExpand(element: StructureViewTreeElement): Boolean {
    return false
  }

  override fun isSmartExpand(): Boolean {
    return false
  }
}