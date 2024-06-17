// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.ChangeWrapper
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.util.containers.JBIterable

internal class VcsLogChangeProcessor(place: String,
                                     val browser: VcsLogChangesBrowser,
                                     handler: ChangesTreeDiffPreviewHandler,
                                     private val isInEditor: Boolean
) : TreeHandlerDiffRequestProcessor(place, browser.viewer, handler) {

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }
}

internal class VcsLogTreeChangeProcessorTracker(val browser: VcsLogChangesBrowser,
                                                editorViewer: DiffEditorViewer,
                                                handler: ChangesTreeDiffPreviewHandler,
                                                updateWhileShown: Boolean)
  : TreeHandlerChangesTreeTracker(browser.viewer, editorViewer, handler, updateWhileShown) {

  override fun track() {
    browser.addListener({ updatePreviewLater(UpdateType.ON_MODEL_CHANGE) }, editorViewer.disposable)

    super.track()
  }
}

internal class VcsLogDiffPreviewHandler(private val browser: VcsLogChangesBrowser) : ChangesTreeDiffPreviewHandler() {
  override fun iterateSelectedChanges(tree: ChangesTree): Iterable<Wrapper> {
    return collectWrappers(browser, VcsTreeModelData.selected(tree))
  }

  override fun iterateAllChanges(tree: ChangesTree): Iterable<Wrapper> {
    return collectWrappers(browser, VcsTreeModelData.all(tree))
  }

  override fun selectChange(tree: ChangesTree, change: Wrapper) {
    browser.selectChange(change.userObject, change.tag)
  }

  companion object {
    private fun collectWrappers(browser: VcsLogChangesBrowser,
                                modelData: VcsTreeModelData): JBIterable<Wrapper> {
      return modelData.iterateNodes()
        .filter(ChangesBrowserChangeNode::class.java)
        .map { node -> MyChangeWrapper(browser, node.userObject, browser.getTag(node.userObject)) }
    }
  }
}

private class MyChangeWrapper(private val browser: VcsLogChangesBrowser, change: Change, tag: ChangesBrowserNode.Tag?)
  : ChangeWrapper(change, tag) {
  override fun createProducer(project: Project?): DiffRequestProducer? {
    return browser.getDiffRequestProducer(change, true)
  }
}
