// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesProvider
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.vcs.log.runInEdtAsync
import com.intellij.vcs.log.ui.frame.VcsLogChangesBrowser
import git4idea.stash.ui.GitStashUi.Companion.GIT_STASH_UI_PLACE
import java.beans.PropertyChangeListener
import java.util.stream.Stream
import javax.swing.JTree

class GitStashDiffPreview(project: Project,
                          private val tree: ChangesTree,
                          private val isInEditor: Boolean,
                          parentDisposable: Disposable)
  : ChangeViewDiffRequestProcessor(project, GIT_STASH_UI_PLACE) {
  private val disposableFlag = Disposer.newCheckedDisposable()

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

  override fun selectChange(change: Wrapper) {
    VcsLogChangesBrowser.selectObjectWithTag(tree, change.userObject, change.tag)
  }

  private fun wrap(treeModelData: VcsTreeModelData): Stream<Wrapper> {
    return treeModelData.userObjectsStream(SavedPatchesProvider.ChangeObject::class.java).map { MyChangeWrapper(it) }
  }

  private inner class MyChangeWrapper(private val change: SavedPatchesProvider.ChangeObject) : Wrapper(), PresentableChange by change {
    override fun getUserObject(): Any = change
    override fun getPresentableName(): String = change.filePath.name
    override fun createProducer(project: Project?): DiffRequestProducer? = change.createDiffRequestProducer(project)
    override fun getTag(): ChangesBrowserNode.Tag? = change.tag
  }
}
