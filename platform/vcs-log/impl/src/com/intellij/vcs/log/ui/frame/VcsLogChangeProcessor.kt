// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData

class VcsLogChangeProcessor internal constructor(project: Project,
                                                 private val myBrowser: VcsLogChangesBrowser,
                                                 private val myIsInEditor: Boolean,
                                                 disposable: Disposable)
  : ChangeViewDiffRequestProcessor(project, if (myIsInEditor) DiffPlaces.DEFAULT else DiffPlaces.VCS_LOG_VIEW) {

  init {
    Disposer.register(disposable, this)

    myBrowser.addListener({ updatePreviewLater() }, this)
    myBrowser.viewer.addSelectionListener({ this.updatePreviewLater() }, this)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !myIsInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  override fun iterateSelectedChanges(): Iterable<Wrapper> {
    return wrap(VcsTreeModelData.selected(myBrowser.viewer))
  }

  override fun iterateAllChanges(): Iterable<Wrapper> {
    return wrap(VcsTreeModelData.all(myBrowser.viewer))
  }

  private fun wrap(modelData: VcsTreeModelData): Iterable<Wrapper> {
    return wrap(myBrowser, modelData)
  }

  override fun selectChange(change: Wrapper) {
    myBrowser.selectChange(change.userObject, change.tag)
  }

  private fun updatePreviewLater() {
    ApplicationManager.getApplication().invokeLater { updatePreview(myIsInEditor || component.isShowing) }
  }

  fun updatePreview(state: Boolean) {
    // We do not have local changes here, so it's OK to always use `fromModelRefresh == false`
    updatePreview(state, false)
  }

  private class MyChangeWrapper(private val myBrowser: VcsLogChangesBrowser, change: Change, tag: ChangesBrowserNode.Tag?)
    : ChangeWrapper(change, tag) {
    override fun createProducer(project: Project?): DiffRequestProducer? {
      return myBrowser.getDiffRequestProducer(change, true)
    }
  }

  companion object {
    fun wrap(browser: VcsLogChangesBrowser, modelData: VcsTreeModelData): Iterable<Wrapper> {
      return modelData.iterateNodes()
        .filter(ChangesBrowserChangeNode::class.java)
        .map<Wrapper> { n: ChangesBrowserChangeNode -> MyChangeWrapper(browser, n.userObject, browser.getTag(n.userObject)) }
    }

    fun getSelectedOrAll(changesBrowser: VcsLogChangesBrowser): VcsTreeModelData {
      val hasSelection = changesBrowser.viewer.selectionModel.selectionCount != 0
      return if (hasSelection) VcsTreeModelData.selected(changesBrowser.viewer)
      else VcsTreeModelData.all(changesBrowser.viewer)
    }
  }
}
