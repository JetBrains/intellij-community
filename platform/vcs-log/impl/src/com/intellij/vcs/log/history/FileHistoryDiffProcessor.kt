// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.SingleFileDiffPreviewProcessor
import com.intellij.vcs.log.ui.frame.VcsLogAsyncChangesTreeModel

internal class FileHistoryDiffProcessor(project: Project,
                                        private val changeGetter: () -> Change?,
                                        private val isInEditor: Boolean,
                                        disposable: Disposable
) : SingleFileDiffPreviewProcessor(project, if (isInEditor) DiffPlaces.DEFAULT else DiffPlaces.VCS_FILE_HISTORY_VIEW) {
  init {
    Disposer.register(disposable, this)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  override fun getFastLoadingTimeMillis(): Long = 10

  override fun getCurrentRequestProvider(): DiffRequestProducer? {
    val change = changeGetter() ?: return null
    return VcsLogAsyncChangesTreeModel.createDiffRequestProducer(project!!, change, HashMap())
  }
}