// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.vcs.log.ui.frame.VcsLogChangesBrowser

internal class FileHistoryDiffPreview(project: Project, private val changeGetter: () -> Change?, isInEditor: Boolean,
                                      disposable: Disposable) :
  ChangeViewDiffRequestProcessor(project, if (isInEditor) DiffPlaces.DEFAULT else DiffPlaces.VCS_LOG_VIEW) {

  init {
    myContentPanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)
    Disposer.register(disposable, this)
  }

    override fun hasSelection(): Boolean {
        return true
    }

    override fun getSelectedChanges(): List<Wrapper> = allChanges

  override fun getAllChanges(): List<Wrapper> {
    val change = changeGetter() ?: return emptyList()
    return listOf(MyChangeWrapper(change))
  }

  override fun selectChange(change: Wrapper) {}

  fun updatePreview(state: Boolean) {
    updatePreview(state, false)
  }

  override fun getFastLoadingTimeMillis(): Int {
    return 10
  }

  private inner class MyChangeWrapper internal constructor(private val change: Change) : Wrapper() {

    override fun getUserObject(): Any {
      return change
    }

    override fun createProducer(project: Project?): DiffRequestProducer? {
      return VcsLogChangesBrowser.createDiffRequestProducer(project!!, change, HashMap(), true)
    }
  }
}