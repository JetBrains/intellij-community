// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.external.ExternalDiffTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChainBackedDiffPreviewProvider
import com.intellij.openapi.vcs.changes.EditorTabPreviewBase
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.ui.actions.history.CompareRevisionsFromFileHistoryActionProvider
import com.intellij.vcs.log.ui.frame.EditorDiffPreview
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.event.ListSelectionListener

class FileHistoryEditorDiffPreview(project: Project, private val fileHistoryPanel: FileHistoryPanel) :
  EditorDiffPreview(project, fileHistoryPanel), ChainBackedDiffPreviewProvider {

  init {
    init()
  }

  override fun getOwnerComponent(): JComponent = fileHistoryPanel.graphTable

  override fun getEditorTabName(processor: DiffRequestProcessor?): @Nls String =
    VcsLogBundle.message("file.history.diff.preview.editor.tab.name", fileHistoryPanel.filePath.name)

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

  override fun createDiffRequestChain(): DiffRequestChain? {
    val change = fileHistoryPanel.selectedChange ?: return null
    val producer = ChangeDiffRequestProducer.create(project, change) ?: return null
    return SimpleDiffRequestChain.fromProducer(producer)
  }

  override fun updateDiffAction(event: AnActionEvent) {
    val selection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
    CompareRevisionsFromFileHistoryActionProvider.setTextAndDescription(event, selection)
  }

  override fun performDiffAction(): Boolean {
    if (ExternalDiffTool.isEnabled()) {
      val change = fileHistoryPanel.selectedChange
      if (change != null) {
        val diffProducers = ChangeDiffRequestProducer.create(project, change)
          ?.let { ListSelection.createSingleton(it) }
        if (EditorTabPreviewBase.showExternalToolIfNeeded(project, diffProducers)) {
          return true
        }
      }
    }

    return super.performDiffAction()
  }
}
