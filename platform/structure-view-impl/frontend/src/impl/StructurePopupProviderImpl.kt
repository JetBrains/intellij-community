// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend.impl

import com.intellij.ide.structureView.newStructureView.StructurePopup
import com.intellij.ide.structureView.newStructureView.StructurePopupProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.structureView.frontend.FileStructurePopup
import com.intellij.platform.structureView.frontend.uiModel.StructureUiModelImpl
import java.util.function.Consumer

class StructurePopupProviderImpl: StructurePopupProvider {
  override fun createPopup(
    project: Project,
    fileEditor: FileEditor,
    callbackAfterNavigation: Consumer<AbstractTreeNode<*>>?
  ): StructurePopup? {
    if (!Registry.`is`("frontend.structure.popup")) return null
    val file = fileEditor.file
    return FileStructurePopup(project, fileEditor, StructureUiModelImpl(fileEditor, file, project))
  }
}