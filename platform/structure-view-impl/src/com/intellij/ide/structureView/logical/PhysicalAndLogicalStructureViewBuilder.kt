// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.ide.structureView.StructureView
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewService.Companion.getInstance
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class PhysicalAndLogicalStructureViewBuilder(
  private val physicalBuilder: StructureViewBuilder,
  private val psiFile: PsiFile,
): StructureViewBuilder {

  override fun createStructureView(fileEditor: FileEditor?, project: Project): StructureView {
    val logicalBuilder = getInstance(project).getLogicalStructureBuilder(psiFile)
    if (logicalBuilder == null) return createPhysicalStructureView(fileEditor, project)

    return StructureViewComposite(
      StructureViewComposite.StructureViewDescriptor("Logical", logicalBuilder.createStructureView(fileEditor, project), null),
      StructureViewComposite.StructureViewDescriptor("Physical", physicalBuilder.createStructureView(fileEditor, project), null)
    )
  }

  fun createPhysicalStructureView(fileEditor: FileEditor?, project: Project): StructureView {
    return physicalBuilder.createStructureView(fileEditor, project)
  }

}