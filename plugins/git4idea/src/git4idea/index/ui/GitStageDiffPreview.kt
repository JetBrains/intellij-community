// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.vcs.log.runInEdtAsync
import git4idea.index.GitStageTracker
import git4idea.index.GitStageTrackerListener
import git4idea.index.createTwoSidesDiffRequestProducer
import java.util.stream.Stream

class GitStageDiffPreview(project: Project, private val tree: ChangesTree, tracker: GitStageTracker, parent: Disposable) :
  ChangeViewDiffRequestProcessor(project, "Stage") {

  init {
    myContentPanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)
    tree.addSelectionListener(Runnable {
      val modelUpdateInProgress = tree.isModelUpdateInProgress
      runInEdtAsync(this) { updatePreview(component.isShowing, modelUpdateInProgress) }
    }, this)
    tracker.addListener(object : GitStageTrackerListener {
      override fun update() {
        updatePreview(component.isShowing, true)
      }
    }, this)
    Disposer.register(parent, this)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean = false

  fun getToolbarWrapper(): com.intellij.ui.components.panels.Wrapper = myToolbarWrapper

  override fun selectChange(change: Wrapper) {
  }

  override fun getSelectedChanges(): Stream<Wrapper?> {
    val hasSelection = tree.selectionModel.selectionCount != 0
    return wrap(if (hasSelection) VcsTreeModelData.selected(tree) else VcsTreeModelData.all(tree))
  }

  override fun getAllChanges(): Stream<Wrapper?> {
    return wrap(VcsTreeModelData.all(tree))
  }

  private fun wrap(modelData: VcsTreeModelData): Stream<Wrapper?> {
    return modelData.userObjectsStream(GitFileStatusNode::class.java).map { info -> GitFileStatusNodeWrapper(info) }
  }

  private class GitFileStatusNodeWrapper(val node: GitFileStatusNode) : Wrapper() {
    override fun getPresentableName(): String? = node.filePath.name

    override fun getUserObject(): Any = node

    override fun createProducer(project: Project?): DiffRequestProducer? {
      return createTwoSidesDiffRequestProducer(project!!, node)
    }
  }
}