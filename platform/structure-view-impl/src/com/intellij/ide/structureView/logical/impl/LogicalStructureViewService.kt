// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl

import com.intellij.ide.structureView.logical.model.LogicalStructureAssembledModel
import com.intellij.ide.structureView.*
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
    if (!psiFile.isValid) return null
    val assembledModel = LogicalStructureAssembledModel.getInstance(project, psiFile)
    try {
      if (assembledModel.getChildren().isEmpty()) return null
    } catch (_: Throwable) {
      return null
    }
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