// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.ide.structureView.StructureView
import com.intellij.ide.structureView.StructureViewBundle
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewService.Companion.getInstance
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class PhysicalAndLogicalStructureViewBuilder(
  private val physicalBuilder: TreeBasedStructureViewBuilder,
  private val psiFile: PsiFile,
): TreeBasedStructureViewBuilder() {

  override fun createStructureView(fileEditor: FileEditor?, project: Project): StructureView {
    val logicalBuilder = getInstance(project).getLogicalStructureBuilder(psiFile)
    if (logicalBuilder == null) return createPhysicalStructureView(fileEditor, project)

    return StructureViewComposite(
      StructureViewComposite.StructureViewDescriptor(
        StructureViewBundle.message("structureview.tab.logical"),
        logicalBuilder.createStructureView(fileEditor, project),
        null
      ),
      StructureViewComposite.StructureViewDescriptor(
        StructureViewBundle.message("structureview.tab.physical"),
        physicalBuilder.createStructureView(fileEditor, project),
        null
      )
    )
  }

  override fun createStructureViewModel(editor: Editor?): StructureViewModel {
    return physicalBuilder.createStructureViewModel(editor)
  }

  fun createPhysicalStructureView(fileEditor: FileEditor?, project: Project): StructureView {
    return physicalBuilder.createStructureView(fileEditor, project)
  }

}