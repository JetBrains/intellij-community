// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.frame.EditorDiffPreview
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.event.ListSelectionListener

class FileHistoryEditorDiffPreview(project: Project, uiProperties: VcsLogUiProperties, private val fileHistoryPanel: FileHistoryPanel) :
  EditorDiffPreview(uiProperties, fileHistoryPanel) {

  init {
    init(project)
  }

  override fun getOwnerComponent(): JComponent = fileHistoryPanel.graphTable

  override fun getEditorTabName(): @Nls String = fileHistoryPanel.filePath.name

  override fun addSelectionListener(listener: () -> Unit) {
    val selectionListener = ListSelectionListener {
      if (!fileHistoryPanel.graphTable.selectionModel.isSelectionEmpty) {
        listener()
      }
    }

    fileHistoryPanel.graphTable.selectionModel.addListSelectionListener(selectionListener)
    Disposer.register(owner, Disposable { fileHistoryPanel.graphTable.selectionModel.removeListSelectionListener(selectionListener) })
  }

  override fun createDiffRequestProcessor(): DiffRequestProcessor {
    val preview: FileHistoryDiffProcessor = fileHistoryPanel.createDiffPreview(true)
    preview.updatePreview(true)
    return preview
  }

}