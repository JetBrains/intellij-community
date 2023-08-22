// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.editor.DiffRequestProcessorEditor
import com.intellij.diff.tools.combined.CombinedDiffModelRepository
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.vcs.changes.actions.diff.COMBINED_DIFF_PREVIEW_TAB_NAME
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture

private class VcsEditorTabTitleProvider : EditorTabTitleProvider, DumbAware {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    return getEditorTabName(project, file)
  }

  override fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): String? {
    return getEditorTabName(project, virtualFile)
  }

  private fun getEditorTabName(project: Project, file: VirtualFile): @TabTitle String? {
    if (file !is PreviewDiffVirtualFile && file !is CombinedDiffPreviewVirtualFile) {
      return null
    }
    val supplier = {
      val editors = FileEditorManager.getInstance(project).getEditors(file)
      val editor = ContainerUtil.findInstance(editors, DiffRequestProcessorEditor::class.java)
      val processor = editor?.processor
      if (file is PreviewDiffVirtualFile) {
        file.provider.getEditorTabName(processor)
      }
      else {
        val sourceId = (file as CombinedDiffPreviewVirtualFile).sourceId
        val diffModel = project.service<CombinedDiffModelRepository>().findModel(sourceId)
        diffModel?.context?.getUserData(COMBINED_DIFF_PREVIEW_TAB_NAME)?.invoke()
      }
    }
    if (EDT.isCurrentThreadEdt()) {
      return supplier()
    }

    @Suppress("DEPRECATION")
    val future = project.coroutineScope.async(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      blockingContext {
        supplier()
      }
    }.asCompletableFuture()
    return ProgressIndicatorUtils.awaitWithCheckCanceled(future)
  }
}
