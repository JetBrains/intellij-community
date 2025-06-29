// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.ide.structureView.*
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewService.Companion.getInstance
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.client.currentSession
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

class PhysicalAndLogicalStructureViewBuilder(
  private val physicalBuilder: TreeBasedStructureViewBuilder,
  private val psiFile: PsiFile,
): TreeBasedStructureViewBuilder() {

  @ApiStatus.Internal
  companion object {
    fun wrapPhysicalBuilderIfPossible(physicalBuilder: StructureViewBuilder?, psiFile: PsiFile): StructureViewBuilder? {
      if (physicalBuilder !is TreeBasedStructureViewBuilder) return physicalBuilder
      if (ApplicationManager.getApplication().isUnitTestMode()
          || !Registry.`is`("logical.structure.enabled", true)
          || (AppMode.isRemoteDevHost() && !Registry.`is`("remoteDev.toolwindow.structure.lux.enabled", false))
          || application.currentSession.isGuest) {
        return physicalBuilder
      }
      return PhysicalAndLogicalStructureViewBuilder(physicalBuilder, psiFile)
    }
  }

  override fun createStructureView(fileEditor: FileEditor?, project: Project): StructureView {
    val logicalBuilder = getInstance(psiFile.project).getLogicalStructureBuilder(psiFile)
    return createStructureView(logicalBuilder, fileEditor, project)
  }

  override suspend fun createStructureViewSuspend(fileEditor: FileEditor?, project: Project): StructureView {
    val logicalBuilder = smartReadAction(project) {
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
        StructureViewTab.LOGICAL.title,
        logicalBuilder.createStructureView(fileEditor, project),
        null
      ),
      StructureViewComposite.StructureViewDescriptor(
        StructureViewTab.PHYSICAL.title,
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