// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.ui.frame.VcsLogChangesBrowser

internal class FileHistoryDiffPreview(project: Project, private val changeGetter: () -> Change?,
                                      disposable: Disposable) : ChangeViewDiffRequestProcessor(project, DiffPlaces.VCS_LOG_VIEW) {
  init {
    myContentPanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)
    Disposer.register(disposable, this)
  }

  override fun getSelectedChanges(): List<ChangeViewDiffRequestProcessor.Wrapper> = allChanges

  override fun getAllChanges(): List<ChangeViewDiffRequestProcessor.Wrapper> {
    val change = changeGetter() ?: return emptyList()
    return listOf(MyChangeWrapper(change))
  }

  override fun selectChange(change: ChangeViewDiffRequestProcessor.Wrapper) {}

  fun updatePreview(state: Boolean) {
    if (state) {
      refresh(false)
    }
    else {
      clear()
    }
  }

  override fun getFastLoadingTimeMillis(): Int {
    return 10
  }

  private inner class MyChangeWrapper internal constructor(private val change: Change) : ChangeViewDiffRequestProcessor.Wrapper() {

    override fun getUserObject(): Any {
      return change
    }

    override fun createProducer(project: Project?): DiffRequestProducer? {
      return VcsLogChangesBrowser.createDiffRequestProducer(project!!, change, ContainerUtil.newHashMap(), true)
    }
  }
}
