// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.util.ui.tree.TreeUtil
import java.beans.PropertyChangeListener
import java.util.stream.Stream
import javax.swing.JTree
import javax.swing.SwingUtilities

class SimpleTreeDiffRequestProcessor(
  project: Project,
  place: String,
  private val tree: ChangesTree,
  parentDisposable: Disposable
) : ChangeViewDiffRequestProcessor(project, place) {

  init {
    Disposer.register(parentDisposable, this)

    tree.addSelectionListener(Runnable {
      updatePreviewLater(false)
    }, this)
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, PropertyChangeListener {
      updatePreviewLater(false)
    })
    updatePreviewLater(false)
  }

  private fun updatePreviewLater(modelUpdateInProgress: Boolean) {
    SwingUtilities.invokeLater { if (!isDisposed) updatePreview(component.isShowing, modelUpdateInProgress) }
  }

  override fun getSelectedChanges(): Stream<Wrapper> {
    return wrap(VcsTreeModelData.selected(tree))
  }

  override fun getAllChanges(): Stream<Wrapper> {
    return wrap(VcsTreeModelData.all(tree))
  }

  override fun selectChange(change: Wrapper) {
    val node = TreeUtil.findNodeWithObject(tree.root, change.userObject) ?: return
    TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false)
  }

  private fun wrap(treeModelData: VcsTreeModelData): Stream<Wrapper> {
    return treeModelData.userObjectsStream(Change::class.java).map { ChangeWrapper(it) }
  }
}