// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.vcs.log.runInEdtAsync
import com.intellij.vcs.log.ui.frame.VcsLogChangesBrowser
import git4idea.stash.ui.GitStashUi.Companion.GIT_STASH_UI_PLACE
import one.util.streamex.StreamEx
import java.beans.PropertyChangeListener
import java.util.stream.Stream
import javax.swing.JTree

abstract class GitStashDiffPreview(project: Project,
                                   private val tree: ChangesTree,
                                   private val isInEditor: Boolean,
                                   parentDisposable: Disposable)
  : ChangeViewDiffRequestProcessor(project, GIT_STASH_UI_PLACE) {
  private val disposableFlag = Disposer.newCheckedDisposable()

  val toolbarWrapper get() = myToolbarWrapper

  init {
    tree.addSelectionListener(Runnable {
      updatePreviewLater(false)
    }, this)
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, PropertyChangeListener {
      updatePreviewLater(false)
    })

    Disposer.register(parentDisposable, this)
    Disposer.register(this, disposableFlag)

    updatePreviewLater(false)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  private fun updatePreviewLater(modelUpdateInProgress: Boolean) {
    runInEdtAsync(disposableFlag) { updatePreview(component.isShowing, modelUpdateInProgress) }
  }

  override fun getSelectedChanges(): Stream<Wrapper> {
    return wrap(VcsTreeModelData.selected(tree))
  }

  override fun getAllChanges(): Stream<Wrapper> {
    return wrap(VcsTreeModelData.all(tree))
  }

  protected abstract fun getTag(change: Change): ChangesBrowserNode.Tag?

  override fun selectChange(change: Wrapper) {
    VcsLogChangesBrowser.selectObjectWithTag(tree, change.userObject, change.tag)
  }

  private fun wrap(treeModelData: VcsTreeModelData): Stream<Wrapper> {
    return StreamEx.of(treeModelData.nodesStream()).select(ChangesBrowserChangeNode::class.java).map {
      MyChangeWrapper(it.userObject, getTag(it.userObject))
    }
  }

  private class MyChangeWrapper(change: Change, tag: ChangesBrowserNode.Tag?) : ChangeWrapper(change, tag) {
    override fun createProducer(project: Project?): DiffRequestProducer? {
      return ChangeDiffRequestProducer.create(project, change, mapOf(Pair(ChangeDiffRequestProducer.TAG_KEY, tag)))
    }
  }
}
