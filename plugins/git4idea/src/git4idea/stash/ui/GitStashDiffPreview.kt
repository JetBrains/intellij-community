// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.actions.diff.SelectionAwareGoToChangePopupActionProvider
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.log.runInEdtAsync
import git4idea.stash.ui.GitStashUi.Companion.GIT_STASH_UI_PLACE
import java.beans.PropertyChangeListener
import java.util.stream.Stream
import javax.swing.JTree
import kotlin.streams.toList

class GitStashDiffPreview(project: Project, private val tree: ChangesTree, isInEditor: Boolean, parentDisposable: Disposable) :
  ChangeViewDiffRequestProcessor(project, GIT_STASH_UI_PLACE) {

  val toolbarWrapper get() = myToolbarWrapper

  init {
    if (!isInEditor) {
      myContentPanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }
    tree.addSelectionListener(Runnable {
      updatePreviewLater(false)
    }, this)
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, PropertyChangeListener {
      updatePreviewLater(false)
    })

    Disposer.register(parentDisposable, this)

    updatePreviewLater(false)
  }

  private fun updatePreviewLater(modelUpdateInProgress: Boolean) {
    runInEdtAsync(this) { updatePreview(component.isShowing, modelUpdateInProgress) }
  }

  override fun getSelectedChanges(): Stream<Wrapper> {
    if (tree.selectionCount == 0) return allChanges
    return wrap(VcsTreeModelData.selected(tree))
  }

  override fun getAllChanges(): Stream<Wrapper> {
    return wrap(VcsTreeModelData.all(tree))
  }

  override fun createGoToChangeAction(): AnAction {
    return MyGoToChangePopupProvider().createGoToChangeAction()
  }

  private inner class MyGoToChangePopupProvider : SelectionAwareGoToChangePopupActionProvider() {
    override fun getChanges(): List<PresentableChange> {
      return allChanges.toList()
    }

    override fun select(change: PresentableChange) {
      (change as? Wrapper)?.run(::selectChange)
    }

    override fun getSelectedChange(): PresentableChange? {
      return currentChange
    }
  }

  override fun selectChange(change: Wrapper) {
    val node = TreeUtil.findNodeWithObject(tree.root, change.userObject) ?: return
    TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean = false

  private fun wrap(treeModelData: VcsTreeModelData): Stream<Wrapper> {
    return treeModelData.userObjectsStream(Change::class.java).map { ChangeWrapper(it) }
  }
}
