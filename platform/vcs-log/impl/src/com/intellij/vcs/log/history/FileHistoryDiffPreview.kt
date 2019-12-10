// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.diff.requests.NoDiffRequest
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.DiffPreviewUpdateProcessor
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.vcs.log.ui.frame.VcsLogChangesBrowser

internal class FileHistoryDiffPreview(project: Project,
                                      private val changeGetter: () -> Change?,
                                      isInEditor: Boolean,
                                      disposable: Disposable
) : CacheDiffRequestProcessor.Simple(project, if (isInEditor) DiffPlaces.DEFAULT else DiffPlaces.VCS_LOG_VIEW),
    DiffPreviewUpdateProcessor {

  init {
    myContentPanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)
    Disposer.register(disposable, this)
  }

  fun updatePreview(state: Boolean) {
    if (state) {
      refresh(false)
    }
    else {
      clear()
    }
  }

  override fun clear() {
    applyRequest(NoDiffRequest.INSTANCE, false, null)
  }

  override fun refresh(fromModelRefresh: Boolean) {
    updateRequest()
  }

  override fun getFastLoadingTimeMillis(): Int {
    return 10
  }

  override fun getCurrentRequestProvider(): DiffRequestProducer? {
    val change = changeGetter() ?: return null
    return VcsLogChangesBrowser.createDiffRequestProducer(project!!, change, HashMap(), true)
  }
}