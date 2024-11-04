// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.ide.structureView.*
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewService.Companion.getInstance
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhysicalAndLogicalStructureViewBuilder(
  private val physicalBuilder: TreeBasedStructureViewBuilder,
  private val psiFile: PsiFile,
): TreeBasedStructureViewBuilder() {

  override fun createStructureView(fileEditor: FileEditor?, project: Project): StructureView {
    val logicalBuilder = getInstance(psiFile.project).getLogicalStructureBuilder(psiFile)
    return createStructureView(logicalBuilder, fileEditor, project)
  }

  override suspend fun createStructureViewSuspend(fileEditor: FileEditor?, project: Project): StructureView {
    val logicalBuilder = readAction {
      getInstance(psiFile.project).getLogicalStructureBuilder(psiFile)
    }
    return withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        createStructureView(logicalBuilder, fileEditor, project)
      }
    }
  }

  private fun createStructureView(
    logicalBuilder: StructureViewBuilder?,
    fileEditor: FileEditor?,
    project: Project,
  ): StructureView {
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